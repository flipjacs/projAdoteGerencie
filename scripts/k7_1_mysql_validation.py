#!/usr/bin/env python3
"""Opt-in K7.1 real HTTP validation. Only the dedicated patas-k71-isolado fixture.
Secrets are read/generated in the private /tmp directory; output contains only
HTTP metadata, synthetic record IDs and schema checks, never clinical bodies/JWTs.
"""
import json, os, pathlib, secrets, subprocess, urllib.request, urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[1]
PRIVATE = pathlib.Path(pathlib.Path('/tmp/patas-k71-current').read_text().strip())
BASE = 'http://127.0.0.1:18083'
OUT = ROOT / 'artifacts/k7.1'
ENV = dict(line.split('=', 1) for line in (PRIVATE / 'mysql.env').read_text().splitlines())
assert ENV['MYSQL_DATABASE'] == 'patas_k71'
rows = []

def sql(statement):
    p = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD='+ENV['MYSQL_ROOT_PASSWORD'],
                        'patas-k71-isolado', 'mysql', '-uroot', '-N', '--default-character-set=utf8mb4', 'patas_k71'],
                       input=statement, text=True, capture_output=True)
    if p.returncode:
        raise AssertionError('MySQL validation statement failed (private diagnostic retained)')
    return p.stdout.strip()

assert sql('SELECT DATABASE();') == 'patas_k71'

def call(label, method, path, expected=200, role=None, body=None):
    headers = {'Content-Type': 'application/json'}
    if role:
        headers['Authorization'] = 'Bearer '+tokens[role]
    req = urllib.request.Request(BASE+path, data=None if body is None else json.dumps(body, ensure_ascii=False).encode(),
                                 headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            status, payload = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, payload = e.code, e.read()
    rows.append(dict(step=label, method=method, path=path, status=status, expected=expected, passed=status == expected))
    if status != expected:
        raise AssertionError(f'{label}: HTTP {status}, esperado {expected}')
    return json.loads(payload) if payload else None

tokens = {}
ids = {}
try:
    call('health versão K7.1', 'GET', '/actuator/health')
    store = PRIVATE / 'accounts.json'
    if store.exists():
        accounts = json.loads(store.read_text())
    else:
        assert sql('SELECT COUNT(*) FROM usuario;') == '0', 'Destino não é fixture K7.1 vazia'
        sql("INSERT INTO ong(id,nome,email) VALUES(7011,'ONG K71 A','a@k71.invalid'),(7012,'ONG K71 B','b@k71.invalid');")
        accounts = {}
        for n, (role, profile, ong) in enumerate([
            ('admin_a','administrador',7011), ('admin_b','administrador',7012),
            ('vet','veterinario',7011), ('volunteer','voluntario',7011),
            ('donor_a','usuario',None), ('donor_b','usuario',None), ('platform','admin_plataforma',None)], 7101):
            password = secrets.token_urlsafe(24)
            p = subprocess.run(['php','-r','$s=stream_get_contents(STDIN);echo password_hash($s,PASSWORD_BCRYPT);'],
                               input=password,text=True,capture_output=True,check=True)
            email = role+'@k71.invalid'
            sql(f"INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES({n},{ong if ong else 'NULL'},'K71 {role}','{email}','{p.stdout}','{profile}',true);")
            accounts[role] = dict(email=email, senha=password)
        store.write_text(json.dumps(accounts)); store.chmod(0o600)
    for role, account in accounts.items():
        tokens[role] = call('login '+role,'POST','/api/auth/login',body=account)['accessToken']
    animal = call('criar animal isolado','POST','/api/animais',201,'admin_a',dict(nome='Animal K71',especie='CAO',sexo='FEMEA',porte='MEDIO',dataEntrada='2020-01-01'))
    aid = ids['animal'] = animal['id']; base = f'/api/ong/animais/{aid}/prontuarios'
    payload = dict(tipo='CONSULTA',titulo='Atendimento de teste K71',descricao='Avaliação sintética',dataProcedimento='2020-01-01',observacoes='Observação sintética')
    rec = call('criar consulta veterinário','POST',base,201,'vet',payload); rid = ids['registro'] = rec['id']
    assert rec['registradoPor']['id'] == 7103 and rec['versao'] == 0
    for kind in ['VACINA','EXAME','CIRURGIA','MEDICACAO','VERMIFUGACAO','TRATAMENTO','OBSERVACAO']:
        call('tipo '+kind,'POST',base,201,'admin_a',dict(payload,tipo=kind))
    call('consulta sem token','GET',base,401)
    call('consulta voluntário','GET',base,role='volunteer')
    call('voluntário sem escrita','POST',base,403,'volunteer',payload)
    call('global sem acesso operacional','GET',base,403,'platform')
    call('outra ONG oculta animal','GET',base,404,'admin_b')
    call('usuário sem acesso institucional','GET',base,403,'donor_a')
    for extra in [dict(ongId=7012),dict(registradoPor=7102),dict(dataProcedimento='2999-01-01'),dict(tipo='OUTRO')]:
        call('rejeitar entrada inválida','POST',base,400,'vet',dict(payload,**extra))
    call('paginação excessiva','GET',base+'?page=2147483647&size=100',400,'vet')
    filtered = call('filtros e paginação','GET',base+'?tipo=VACINA&de=2020-01-01&ate=2020-01-01&size=1',role='vet')
    assert filtered['totalElements'] == 1
    corrected = call('correção auditada','PATCH',base+f'/{rid}/corrigir',role='admin_a',body=dict(titulo='Correção K71',observacoes=None,justificativa='Revisão de teste',versao=0))
    assert corrected['registradoPor']['id'] == 7103 and corrected['versao'] == 1 and corrected.get('observacoes') is None
    call('versão obsoleta','PATCH',base+f'/{rid}/corrigir',409,'vet',dict(titulo='Versão antiga',justificativa='Teste conflito',versao=0))
    hist = call('histórico voluntário','GET',base+f'/{rid}/correcoes',role='volunteer')
    assert hist['totalElements'] == 1 and hist['content'][0]['dadosAnteriores']['titulo'] == payload['titulo']
    assert hist['content'][0]['dadosNovos']['titulo'] == 'Correção K71'
    assert sql(f'SELECT versao FROM prontuario WHERE id={rid};') == '1'
    long_input = dict(payload,titulo='🐾'*120,descricao='X'+'\u0001'*9998+'Z',medicamentos='X'+'\u0001'*4998+'Z',observacoes='X'+'\u0001'*4998+'Z')
    large = call('criar campos máximos Unicode','POST',base,201,'vet',long_input)
    call('snapshot acima de TEXT','PATCH',base+f"/{large['id']}/corrigir",role='vet',body=dict(titulo='Revisão longa',justificativa='Teste capacidade snapshot',versao=0))
    snap_bytes = int(sql(f"SELECT OCTET_LENGTH(dados_anteriores) FROM correcao_prontuario WHERE prontuario_id={large['id']};"))
    assert snap_bytes > 65535
    adopted = f'/api/me/animais-adotados/{aid}/prontuarios'
    call('adotante antes da solicitação','GET',adopted,404,'donor_a')
    current = call('animal atual','GET',f'/api/ong/animais/{aid}',role='admin_a')
    call('animal disponível','PATCH',f'/api/ong/animais/{aid}/situacao',role='admin_a',body=dict(situacao='DISPONIVEL',versao=current['versao']))
    call('publicar animal','PATCH',f'/api/ong/animais/{aid}/publicar',role='admin_a')
    request = call('solicitar adoção','POST','/api/adoption-requests',201,'donor_a',dict(animalId=aid))
    reqid = ids['solicitacao'] = request['id']
    call('aprovar adoção','PATCH',f'/api/ong/adoption-requests/{reqid}/aprovar',role='admin_a')
    call('aprovada ainda não libera prontuário','GET',adopted,404,'donor_a')
    call('entrega formal concluída','POST',f'/api/ong/adoption-requests/{reqid}/confirmar-entrega',role='admin_a',body=dict(entregaConfirmada=True))
    call('adotante após entrega','GET',adopted,role='donor_a')
    personal = call('detalhe adotante','GET',adopted+f'/{rid}',role='donor_a')
    assert all(k not in personal for k in ['registradoPor','ongId','versao','criadoEm','atualizadoEm'])
    call('outro usuário impedido','GET',adopted,404,'donor_b')
    call('adotante não altera','PATCH',adopted+f'/{rid}/corrigir',403,'donor_a',dict(titulo='Indevido'))
    call('adotante sem auditoria institucional','GET',adopted+f'/{rid}/correcoes',403,'donor_a')
    call('novo registro pós adoção bloqueado','POST',base,409,'vet',payload)
    call('correção pós adoção autorizada','PATCH',base+f'/{rid}/corrigir',role='vet',body=dict(descricao='Complemento sintético',justificativa='Revisão posterior',versao=1))
    assert sql(f'SELECT COUNT(*) FROM correcao_prontuario WHERE prontuario_id={rid};') == '2'
    assert sql(f"SELECT s.status FROM solicitacao_adocao s JOIN adocao a ON a.solicitacao_id=s.id WHERE a.animal_id={aid};") == 'CONCLUIDA'
    schema = sql("SELECT version,success FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank;")
    assert '10\t1' in schema
    sql("UPDATE ong SET situacao='SUSPENSA' WHERE id=7011;")
    call('ONG suspensa bloqueia equipe','GET',base,403,'vet')
    call('adoção mantém leitura com ONG suspensa','GET',adopted,role='donor_a')
    sql("UPDATE ong SET situacao='ATIVA' WHERE id=7011;")
    result = dict(verdict='PASS',database='patas_k71',container='patas-k71-isolado',mysql='8.4',port=13318,apiPort=18083,
                  migrations=schema.splitlines(),ids=ids,snapshotBytes=snap_bytes,requests=rows)
except Exception as e:
    result = dict(verdict='FAIL',error=str(e),ids=ids,requests=rows)
    raise
finally:
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'mysql-http.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(dict(verdict=result['verdict'],requests=len(rows),snapshotBytes=result.get('snapshotBytes'))))
