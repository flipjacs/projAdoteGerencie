"""Opt-in HTTP integration against the isolated K5 backend; keeps synthetic records.
K5_ADMIN_EMAIL and K5_ADMIN_PASSWORD must be supplied through the environment.
Requires the isolated API at localhost:18081 with V8 applied; never uses a production URL.
"""
import concurrent.futures
import json
import os
import secrets
import threading
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:18081"


def call(method, path, body=None, token=None, expected=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(
        BASE + path, method=method, headers=headers,
        data=json.dumps(body).encode() if body is not None else None,
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if expected is not None:
        assert status == expected, f"{method} {path}: HTTP {status}, expected {expected}"
    return status, json.loads(raw) if raw else None


def race(method, paths, tokens, body=None):
    barrier = threading.Barrier(2)

    def worker(index):
        barrier.wait(timeout=10)
        return call(method, paths[index], body, tokens[index])

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        jobs = [executor.submit(worker, i) for i in range(2)]
        return [job.result(timeout=40) for job in jobs]


def validate():
    admin = call("POST", "/api/auth/login", {
        "email": os.environ["K5_ADMIN_EMAIL"],
        "senha": os.environ["K5_ADMIN_PASSWORD"],
    }, expected=200)[1]["accessToken"]
    suffix = uuid.uuid4().hex[:12]
    users = []
    for index in range(3):
        email, password = f"k5-{suffix}-{index}@teste.invalid", secrets.token_hex(20)
        call("POST", "/api/usuarios/cadastro", {"nome": "Pessoa K5 isolada", "email": email, "senha": password}, expected=201)
        users.append(call("POST", "/api/auth/login", {"email": email, "senha": password}, expected=200)[1]["accessToken"])
    outcomes = []
    for iteration in range(3):
        animal = call("POST", "/api/animais", {
            "nome": f"Animal K5 isolado {suffix} {iteration}", "especie": "CAO",
            "sexo": "FEMEA", "porte": "MEDIO", "dataEntrada": "2026-11-01",
        }, admin, 201)[1]
        animal_id = animal["id"]
        path = f"/api/ong/animais/{animal_id}"
        call("PATCH", path + "/situacao", {"situacao": "DISPONIVEL", "versao": animal["versao"]}, admin, 200)
        call("PATCH", path + "/publicar", token=admin, expected=200)
        duplicate = race("POST", ["/api/adoption-requests"] * 2, [users[0]] * 2, {"animalId": animal_id})
        assert sorted(code for code, _ in duplicate) == [201, 409], "Concurrent pending duplicate accepted"
        first = next(record for code, record in duplicate if code == 201)
        second = call("POST", "/api/adoption-requests", {"animalId": animal_id}, users[1], 201)[1]
        third = call("POST", "/api/adoption-requests", {"animalId": animal_id}, users[2], 201)[1]
        ids = [first["id"], second["id"]]
        approvals = race("PATCH", [f"/api/ong/adoption-requests/{i}/aprovar" for i in ids], [admin] * 2)
        assert sorted(code for code, _ in approvals) == [200, 409], "Conflicting concurrent approvals accepted"
        winner = next(record["id"] for code, record in approvals if code == 200)
        loser = next(i for i in ids if i != winner)
        reserved = call("GET", path, token=admin, expected=200)[1]
        assert reserved["situacao"] == "EM_PROCESSO_ADOCAO"
        active = call("GET", f"/api/ong/adoption-requests?animalId={animal_id}&status=APROVADA", token=admin, expected=200)[1]
        assert active["totalElements"] == 1
        call("PATCH", f"/api/ong/adoption-requests/{winner}/cancelar-aprovacao", {"justificativa": "Teste isolado de liberação"}, admin, 200)
        call("PATCH", f"/api/ong/adoption-requests/{loser}/aprovar", token=admin, expected=200)
        call("POST", f"/api/ong/adoption-requests/{loser}/confirmar-entrega", {"entregaConfirmada": False}, admin, 400)
        completed = race("POST", [f"/api/ong/adoption-requests/{loser}/confirmar-entrega"] * 2, [admin] * 2, {"entregaConfirmada": True})
        assert sorted(code for code, _ in completed) == [200, 409], "Repeated concurrent delivery accepted"
        assert call("GET", path, token=admin, expected=200)[1]["situacao"] == "ADOTADO"
        other = call("GET", f"/api/ong/adoption-requests/{third['id']}", token=admin, expected=200)[1]
        assert other["status"] == "REJEITADA"
        assert other["justificativaRejeicao"] == "Animal adotado por outro solicitante."
        earlier = call("GET", f"/api/ong/adoption-requests/{winner}", token=admin, expected=200)[1]
        assert earlier["status"] == "CANCELADA" and len(earlier["historico"]) == 3
        outcomes.append({"rodada": iteration + 1, "animalId": animal_id, "criacaoConcorrente": [201, 409], "aprovacaoConcorrente": [200, 409], "entregaConcorrente": [200, 409]})
    print(json.dumps({"resultado": "APROVADO", "rodadasMySQL": outcomes}, ensure_ascii=False))


if __name__ == "__main__":
    validate()
