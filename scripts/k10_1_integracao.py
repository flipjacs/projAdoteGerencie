"""Verificações HTTP e MySQL da K10.1. Executar somente na fixture isolada."""
import json
from decimal import Decimal
from pathlib import Path
import urllib.request
import urllib.error

import k8_2_backend_probe as probe

out = Path(__file__).resolve().parents[1] / 'artifacts/k10.1/integracao'
checks = []
other_admin = json.loads((probe.PRIVATE / 'accounts.json').read_text())['admin_b']
status, identity = probe.request('POST', '/api/auth/login', other_admin)
assert status == 200
foreign = identity['accessToken']

def call(role, method, path, status=200, body=None):
    if role == 'foreign':
        got, data = probe.request(method, path, body, foreign)
    elif role:
        got, data = probe.call(role, method, path, body)
    else:
        try:
            with urllib.request.urlopen(probe.BASE + path, timeout=10) as response:
                got, data = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            got, data = error.code, None
    checks.append(dict(method=method, path=path, role=role, http=got, expected=status))
    assert got == status, checks[-1]
    return data

def db(label, query, expected):
    actual = probe.sql(query)
    checks.append(dict(sql=label, actual=actual, expected=expected))
    assert actual == expected, checks[-1]

def animal(name):
    a=call('admin','POST','/api/animais',201,dict(nome=name,especie='CAO',sexo='FEMEA',porte='MEDIO',dataEntrada='2020-01-01'))
    path=f"/api/ong/animais/{a['id']}"
    call('admin','PATCH',path+'/situacao',body=dict(situacao='DISPONIVEL',versao=a['versao']))
    call('admin','PATCH',path+'/publicar')
    return a['id']

def summary():
    return Decimal(call('admin','GET','/api/ong/finance/resumo')['saldoRealizado'])

try:
    db('MySQL isolado', 'SELECT DATABASE();', 'patas_k81')
    db('Flyway V1–V13', "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL;", ','.join(map(str,range(1,14))))
    for role in ('user','admin','volunteer','vet','platform'):
        identity=call(role,'GET','/api/auth/me')
        checks.append(dict(role=role, perfil=identity['perfil']))
        assert identity['perfil']==dict(user='USUARIO',admin='ADMINISTRADOR',volunteer='VOLUNTARIO',vet='VETERINARIO',platform='ADMIN_PLATAFORMA')[role]
    call('admin','GET','/api/ong/funcionarios')
    call('volunteer','GET','/api/ong/funcionarios',403)
    call('platform','GET','/api/plataforma/ongs')
    a=animal('K101 Integracao')
    call(None,'GET',f'/api/animais/{a}')
    clinical=f'/api/ong/animais/{a}/prontuarios'
    body=dict(tipo='CONSULTA',titulo='Consulta sintética',descricao='Fixture K10.1',dataProcedimento='2020-01-01')
    c=call('vet','POST',clinical,201,body)['id']
    call('volunteer','POST',clinical,403,body)
    call('foreign','GET',clinical,404)
    call(None,'GET',clinical,401)
    edit=f'{clinical}/{c}/corrigir'
    update=dict(titulo='Revisão sintética',justificativa='Conferência de integração',versao=0)
    call('vet','PATCH',edit,body=update)
    call('vet','PATCH',edit,409,update)
    db('Uma correção clínica preservada',f'SELECT COUNT(*) FROM correcao_prontuario WHERE prontuario_id={c};','1')
    s=call('user','POST','/api/sponsorships',201,dict(animalId=a,modalidade='MENSAL',valor='2.50'))
    sid=s['id']; pid=s['primeiraParcela']['id']
    sp=f'/api/me/sponsorships/{sid}'
    call('other','GET',sp,404)
    confirm=f'/api/ong/sponsorships/{sid}/parcelas/{pid}/confirmar'
    call('admin','PATCH',confirm)
    call('admin','PATCH',confirm,409)
    db('Receita de parcela única',f'SELECT COUNT(*) FROM movimentacao_financeira WHERE parcela_apadrinhamento_id={pid};','1')
    x=call('user','POST','/api/adoption-requests',201,dict(animalId=a))['id']
    y=call('other','POST','/api/adoption-requests',201,dict(animalId=a))['id']
    call('admin','PATCH',f'/api/ong/adoption-requests/{x}/aprovar')
    call('admin','PATCH',f'/api/ong/adoption-requests/{y}/aprovar',409)
    assert call('user','GET',sp)['status']=='ATIVO'
    own=f'/api/me/animais-adotados/{a}/prontuarios'
    call('user','GET',own,404)
    call('admin','POST',f'/api/ong/adoption-requests/{x}/confirmar-entrega',body=dict(entregaConfirmada=True))
    db('Situação do animal após entrega',f'SELECT situacao FROM animal WHERE id={a};','ADOTADO')
    assert call('user','GET',sp)['motivoEncerramento']=='ADOCAO_CONCLUIDA'
    call('user','GET',f'{own}/{c}')
    call('other','GET',own,404)
    dead=animal('K101 Encerramento')
    ds=call('other','POST','/api/sponsorships',201,dict(animalId=dead,modalidade='UNICO',valor='1.00'))['id']
    path=f'/api/ong/animais/{dead}'
    current=call('admin','GET',path)
    call('admin','PATCH',path+'/situacao',body=dict(situacao='FALECIDO',versao=current['versao']))
    db('Apadrinhamento encerrado por falecimento',f'SELECT status FROM apadrinhamento WHERE id={ds};','ENCERRADO')
    start=summary()
    d=call('user','POST','/api/donations',201,dict(ongId=8201,valor='9.01'))['id']
    path=f'/api/ong/donations/{d}/confirmar'
    call('admin','PATCH',path)
    call('admin','PATCH',path,409)
    db('Receita de doação única',f'SELECT COUNT(*) FROM movimentacao_financeira WHERE doacao_id={d};','1')
    assert summary()==start+Decimal('9.01')
    m=call('admin','POST','/api/ong/finance/movimentacoes',201,dict(tipo='DESPESA',categoria='ALIMENTACAO',descricao='Despesa sintética K10.1',valor='3.20'))['id']
    path=f'/api/ong/finance/movimentacoes/{m}'
    call('admin','PATCH',path+'/aprovar')
    assert summary()==start+Decimal('9.01')
    call('admin','PATCH',path+'/confirmar-pagamento')
    assert summary()==start+Decimal('5.81')
    db('Despesa realizada',f'SELECT status FROM movimentacao_financeira WHERE id={m};','REALIZADA')
    t=call('admin','POST','/api/ong/transparencia',201,dict(titulo='K10.1 Transparência',periodoInicio='2020-01-01',periodoFim='9999-12-31'))['id']
    path=f'/api/ong/transparencia/{t}'
    call('admin','PATCH',path+'/publicar')
    public=f'/api/public/ongs/8201/transparencia/{t}'
    data=call(None,'GET',public)
    assert set(data)=={'id','ong','titulo','descricao','periodoInicio','periodoFim','publicadoEm','dados'}
    assert set(data['dados'])=={'receitasRealizadas','despesasRealizadas','resultadoLiquido','receitasPorCategoria','despesasPorCategoria','quantidadeMovimentacoes'}
    call('admin','PATCH',path+'/despublicar')
    call(None,'GET',public,404)
    checks.append(dict(veredito='PASS',animalAdotado=a))
finally:
    out.mkdir(parents=True,exist_ok=True)
    (out/'http-mysql.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n')
print(len(checks),'verificações registradas')
