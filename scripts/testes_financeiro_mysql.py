#!/usr/bin/env python3
"""K6.1: representative HTTP checks, restricted to the isolated local API.

K61_TEST_CREDENTIALS points to a protected JSON containing baseUrl, ongA, ongB
and accounts (admin_a/admin_b/donor_a/donor_b/volunteer/vet/platform), each with
email/senha. No credentials or tokens are written to the evidence.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import json
import os
from pathlib import Path
import urllib.error
import urllib.request


def run(config):
    base = config['baseUrl'].rstrip('/')
    if base != 'http://127.0.0.1:18082':
        raise ValueError('Validação permitida somente na API MySQL isolada da K6.1')
    checks = []

    def call(method, path, token=None, data=None, expected=(200,)):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(base + path, None if data is None else json.dumps(data).encode(), headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        checks.append({'method': method, 'path': path, 'http': status})
        if status not in expected:
            raise AssertionError(f'{method} {path}: HTTP {status}, esperado {expected}')
        return status, json.loads(body) if body else None

    tokens = {role: call('POST', '/api/auth/login', data=account)[1]['accessToken'] for role, account in config['accounts'].items()}
    a, b, donor, other, vol = (tokens[x] for x in ['admin_a', 'admin_b', 'donor_a', 'donor_b', 'volunteer'])
    org, external_org = config['ongA'], config['ongB']
    finance, donations, summary = '/api/ong/finance/movimentacoes', '/api/ong/donations', '/api/ong/finance/resumo'
    initial = call('GET', summary, a)[1]

    def donation(value='20.10'):
        return call('POST', '/api/donations', donor, {'ongId': org, 'valor': value, 'mensagem': 'Validação financeira isolada'}, (201,))[1]

    def movement(kind='RECEITA', value='10.20', token=vol):
        return call('POST', finance, token, {'tipo': kind, 'categoria': 'EVENTO' if kind == 'RECEITA' else 'ALIMENTACAO', 'descricao': 'Validação K6.1', 'valor': value}, (201,))[1]

    d = donation()
    assert d['status'] == 'PENDENTE' and d['valor'] == '20.10'
    own = f"/api/me/donations/{d['id']}"
    assert 'doador' not in call('GET', own, donor)[1]
    call('GET', own, other, expected=(404,))
    call('PATCH', own + '/cancelar', other, expected=(404,))
    call('GET', f"{donations}/{d['id']}", b, expected=(404,))
    call('PATCH', f"{donations}/{d['id']}/confirmar", b, expected=(404,))
    call('PATCH', f"{donations}/{d['id']}/confirmar", vol, expected=(403,))
    call('GET', f"{donations}/{d['id']}", vol)
    assert call('PATCH', f"{donations}/{d['id']}/confirmar", a)[1]['status'] == 'CONFIRMADA'
    call('PATCH', f"{donations}/{d['id']}/confirmar", a, expected=(409,))
    call('PATCH', own + '/cancelar', donor, expected=(409,))
    auto = next(x for x in call('GET', finance + '?categoria=DOACAO&size=100', a)[1]['content'] if x['origem']['id'] == d['id'])
    assert auto['valor'] == '20.10' and auto['status'] == 'REALIZADA'
    for action in ['aprovar', 'confirmar-recebimento', 'confirmar-pagamento', 'cancelar']:
        call('PATCH', f"{finance}/{auto['id']}/{action}", a, expected=(409,))

    race = donation('5.55')
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: call('PATCH', f"{donations}/{race['id']}/confirmar", a, expected=(200, 409))[0], range(2)))
    assert sorted(results) == [200, 409]
    rows = call('GET', finance + '?categoria=DOACAO&size=100', a)[1]['content']
    assert sum(x['origem']['id'] == race['id'] for x in rows) == 1
    assert len(call('GET', f"{donations}/{race['id']}", a)[1]['historico']) == 2
    cancelled = donation('9.99')
    assert call('PATCH', f"/api/me/donations/{cancelled['id']}/cancelar", donor)[1]['status'] == 'CANCELADA'
    call('PATCH', f"{donations}/{cancelled['id']}/confirmar", a, expected=(409,))

    revenue = movement()
    assert revenue['status'] == 'PENDENTE' and revenue['origem']['tipo'] == 'MANUAL'
    call('PATCH', f"{finance}/{revenue['id']}/confirmar-recebimento", vol, expected=(403,))
    assert call('PATCH', f"{finance}/{revenue['id']}/confirmar-recebimento", a)[1]['status'] == 'REALIZADA'
    expense = movement('DESPESA', '7.25')
    call('PATCH', f"{finance}/{expense['id']}/confirmar-pagamento", a, expected=(409,))
    call('PATCH', f"{finance}/{expense['id']}/aprovar", vol, expected=(403,))
    with ThreadPoolExecutor(max_workers=2) as pool:
        approvals = list(pool.map(lambda _: call('PATCH', f"{finance}/{expense['id']}/aprovar", a, expected=(200, 409))[0], range(2)))
    assert sorted(approvals) == [200, 409]
    before_payment = call('GET', summary, a)[1]
    assert Decimal(before_payment['despesasAprovadas']) - Decimal(initial['despesasAprovadas']) == Decimal('7.25')
    assert Decimal(before_payment['despesasRealizadas']) == Decimal(initial['despesasRealizadas'])
    assert call('PATCH', f"{finance}/{expense['id']}/confirmar-pagamento", a)[1]['status'] == 'REALIZADA'
    call('PATCH', f"{finance}/{expense['id']}/confirmar-pagamento", a, expected=(409,))
    call('PATCH', f"{finance}/{expense['id']}/cancelar", a, expected=(409,))
    for amount in ['0.10', '0.20']:
        item = movement(value=amount)
        call('PATCH', f"{finance}/{item['id']}/confirmar-recebimento", a)
    pending = movement(value='700.30')
    canceled_expense = movement('DESPESA', '100.00')
    call('PATCH', f"{finance}/{canceled_expense['id']}/aprovar", a)
    result = call('PATCH', f"{finance}/{canceled_expense['id']}/cancelar", a, {'justificativa': 'Despesa cancelada na validação'})[1]
    assert result['status'] == 'CANCELADA' and len(result['historico']) == 3
    assert result['justificativaCancelamento'] == 'Despesa cancelada na validação'
    foreign = movement(value='987.65', token=b)
    assert foreign['ongId'] == external_org
    call('PATCH', f"{finance}/{foreign['id']}/confirmar-recebimento", b)
    call('GET', f"{finance}/{foreign['id']}", a, expected=(404,))
    call('PATCH', f"{finance}/{foreign['id']}/cancelar", a, expected=(404,))

    for role in ['donor_a', 'vet', 'platform']:
        call('GET', finance, tokens[role], expected=(403,))
        call('GET', summary, tokens[role], expected=(403,))
    call('GET', summary, expected=(401,))
    for value in ['0', '-1', '1.234', '1e2', '10000000000.00']:
        call('POST', '/api/donations', donor, {'ongId': org, 'valor': value}, (400,))
    call('POST', '/api/donations', donor, {'ongId': org, 'valor': 1}, (400,))
    call('GET', finance + '?de=2026-12-31&ate=2026-01-01', a, expected=(400,))
    call('GET', finance + '?size=101', a, expected=(400,))
    final = call('GET', summary, a)[1]
    expected_delta = {'receitasRealizadas': '36.15', 'despesasRealizadas': '7.25', 'receitasPendentes': '700.30', 'despesasPendentes': '0.00', 'despesasAprovadas': '0.00', 'saldoRealizado': '28.90'}
    delta = {k: format(Decimal(final[k]) - Decimal(initial[k]), '.2f') for k in final}
    assert delta == expected_delta, delta
    tomorrow = (datetime.now(timezone.utc) + timedelta(days=1)).date().isoformat()
    assert all(Decimal(v) == 0 for v in call('GET', summary + '?de=' + tomorrow, a)[1].values())
    page = call('GET', finance + '?page=1&size=1', a)[1]
    assert page['number'] == 1 and len(page['content']) == 1
    call('GET', donations + '?status=CONFIRMADA&size=1&page=1', vol)
    return {'baseUrl': base, 'mysql': 'patas-k61-isolado / patas_k61 / 13317', 'checks': checks, 'resumoInicial': initial, 'resumoFinal': final, 'delta': delta, 'concorrenciaConfirmacaoDoacao': sorted(results), 'concorrenciaAprovacaoDespesa': sorted(approvals), 'registros': {'doacao': d['id'], 'doacaoConcorrente': race['id'], 'doacaoCancelada': cancelled['id'], 'receita': revenue['id'], 'despesa': expense['id'], 'pendencia': pending['id'], 'despesaCancelada': canceled_expense['id']}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', default='artifacts/k6.1/mysql-http.json')
    args = parser.parse_args()
    config = json.loads(Path(os.environ['K61_TEST_CREDENTIALS']).read_text())
    result = run(config)
    path = Path(args.report)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({'requisicoes': len(result['checks']), 'delta': result['delta'], 'concorrenciaDoacao': result['concorrenciaConfirmacaoDoacao'], 'concorrenciaDespesa': result['concorrenciaAprovacaoDespesa']}, ensure_ascii=False))
