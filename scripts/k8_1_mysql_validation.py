#!/usr/bin/env python3
"""Opt-in real HTTP checks; exclusively patas-k81-isolado/patas_k81:13319.
Private credentials stay in /tmp, artifacts contain only synthetic IDs/statuses.
"""
from decimal import Decimal
import concurrent.futures, datetime, json, os, pathlib, secrets, subprocess, threading, time, urllib.request, urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[1]
PRIVATE=pathlib.Path(pathlib.Path('/tmp/patas-k81-current').read_text().strip())
ENV=dict(line.split('=',1) for line in (PRIVATE/'mysql.env').read_text().splitlines())
BASE='http://127.0.0.1:18084'; OUT=ROOT/'artifacts/k8.1'; OUT.mkdir(parents=True,exist_ok=True)
assert ENV['MYSQL_DATABASE']=='patas_k81'
rows=[]; tokens={}; ids={}
def sqlproc(statement):
 return subprocess.Popen(['docker','exec','-i','-e','MYSQL_PWD='+ENV['MYSQL_ROOT_PASSWORD'],'patas-k81-isolado','mysql','-uroot','-N','--default-character-set=utf8mb4','patas_k81'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
def sql(statement):
 p=sqlproc(statement);out,err=p.communicate(statement)
 if p.returncode: (PRIVATE/'sql-error.log').write_text(err);raise AssertionError('Falha SQL isolado; diagnóstico privado')
 return out.strip()
assert sql('SELECT DATABASE();')=='patas_k81'
def request(method,path,role=None,body=None):
 headers={'Content-Type':'application/json'}
 if role:headers['Authorization']='Bearer '+tokens[role]
 req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=30) as r:status,payload=r.status,r.read()
 except urllib.error.HTTPError as e:status,payload=e.code,e.read()
 return status,json.loads(payload) if payload else None
def call(label,method,path,expected=200,role=None,body=None):
 status,payload=request(method,path,role,body);rows.append(dict(step=label,method=method,path=path,status=status,expected=expected,passed=status==expected))
 assert status==expected,f'{label}: HTTP {status}, esperado {expected}'
 return payload
def race(label,a,b,expected,hold=None):
 blocker=None
 if hold:
  blocker=sqlproc('');blocker.stdin.write(f'START TRANSACTION; SELECT id FROM animal WHERE id={hold} FOR UPDATE; DO SLEEP(3); COMMIT;\n');blocker.stdin.close();time.sleep(.4)
 barrier=threading.Barrier(2)
 def run(args):barrier.wait();return request(*args)[0]
 with concurrent.futures.ThreadPoolExecutor(2) as pool: result=sorted(pool.map(run,[a,b]))
 if blocker:blocker.wait(timeout=10)
 rows.append(dict(step=label,statuses=result,expected=sorted(expected),passed=result==sorted(expected)))
 assert result==sorted(expected),f'{label}: {result}, esperado {expected}'
def animal():
 a=call('criar animal','POST','/api/animais',201,'admin_a',dict(nome='Animal K81',especie='CAO',sexo='FEMEA',porte='MEDIO',dataEntrada='2020-01-01'))
 aid=a['id'];call('disponibilizar animal','PATCH',f'/api/ong/animais/{aid}/situacao',role='admin_a',body=dict(situacao='DISPONIVEL',versao=a['versao']))
 call('publicar animal','PATCH',f'/api/ong/animais/{aid}/publicar',role='admin_a');return aid
def sponsor(aid,mode='MENSAL',role='user_a'):
 return call('criar apadrinhamento '+mode,'POST','/api/sponsorships',201,role,dict(animalId=aid,modalidade=mode,valor='25.50'))
def parts(sid,role='admin_a'):
 return call('consultar parcelas','GET',f'/api/ong/sponsorships/{sid}/parcelas',role=role)['content']
def own(sid):return call('detalhe próprio','GET',f'/api/me/sponsorships/{sid}',role='user_a')
def confirm(sid,pid,expected=200):return call('confirmar parcela','PATCH',f'/api/ong/sponsorships/{sid}/parcelas/{pid}/confirmar',expected,'admin_a')
try:
 call('health K8.1','GET','/actuator/health')
 store=PRIVATE/'accounts.json'
 if not store.exists():
  assert sql('SELECT COUNT(*) FROM usuario;')=='0'
  sql("INSERT INTO ong(id,nome,email) VALUES(8101,'ONG K81 A','a@k81.invalid'),(8102,'ONG K81 B','b@k81.invalid');")
  accounts={}
  for n,(role,profile,ong) in enumerate([('admin_a','administrador',8101),('admin_b','administrador',8102),('volunteer','voluntario',8101),('vet','veterinario',8101),('platform','admin_plataforma',None)],8111):
   password=secrets.token_urlsafe(24);hashed=subprocess.run(['php','-r','$s=stream_get_contents(STDIN);echo password_hash($s,PASSWORD_BCRYPT);'],input=password,text=True,capture_output=True,check=True).stdout
   email=role+'@k81.invalid';sql(f"INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES({n},{ong or 'NULL'},'K81 {role}','{email}','{hashed}','{profile}',true);")
   accounts[role]=dict(email=email,senha=password)
  for role in ['user_a','user_b']:
   account=dict(email=role+'@k81.invalid',senha=secrets.token_urlsafe(24))
   call('cadastro público '+role,'POST','/api/usuarios/cadastro',201,body=dict(nome='K81 '+role,**account));accounts[role]=account
  store.write_text(json.dumps(accounts));store.chmod(0o600)
 else:accounts=json.loads(store.read_text())
 for role,account in accounts.items():tokens[role]=call('login '+role,'POST','/api/auth/login',body=account)['accessToken']
 aid=animal();body=dict(animalId=aid,modalidade='MENSAL',valor='25.50')
 race('criação concorrente mesmo compromisso',('POST','/api/sponsorships','user_a',body),('POST','/api/sponsorships','user_a',body),[201,409],hold=aid)
 if os.environ.get('K81_RACE_ONLY'):result=dict(verdict='PASS',requests=rows);(OUT/'creation-race.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print('Criação concorrente PASS');raise SystemExit(0)
 baseline=call('resumo inicial','GET','/api/ong/finance/resumo',role='admin_a')
 baseline_report=call('categorias iniciais','GET','/api/ong/relatorios/financeiro',role='admin_a')
 aid=animal();s=sponsor(aid);sid=ids['mensal']=s['id'];pid=s['primeiraParcela']['id']
 assert sql(f'SELECT COUNT(*) FROM movimentacao_financeira WHERE parcela_apadrinhamento_id={pid};')=='0'
 assert 'padrinho' not in own(sid)
 call('outro usuário','GET',f'/api/me/sponsorships/{sid}',404,'user_b')
 call('outra ONG','GET',f'/api/ong/sponsorships/{sid}',404,'admin_b')
 for role in ['vet','platform','user_a']:call('perfil sem consulta financeira','GET','/api/ong/sponsorships',403,role)
 call('voluntário consulta','GET','/api/ong/sponsorships',role='volunteer')
 call('voluntário sem confirmação','PATCH',f'/api/ong/sponsorships/{sid}/parcelas/{pid}/confirmar',403,'volunteer')
 path=f'/api/ong/sponsorships/{sid}/parcelas/{pid}/confirmar'
 race('confirmação concorrente',('PATCH',path,'admin_a'),('PATCH',path,'admin_a'),[200,409])
 assert sql(f'SELECT COUNT(*) FROM movimentacao_financeira WHERE parcela_apadrinhamento_id={pid};')=='1';confirm(sid,pid,409)
 month=datetime.date.today().replace(day=1);start=(month-datetime.timedelta(days=1)).replace(day=1)
 sql(f"UPDATE apadrinhamento SET data_inicio='{start}' WHERE id={sid}; UPDATE parcela_apadrinhamento SET vencimento='{start}' WHERE id={pid};")
 race('geração concorrente sem duplicação',('GET',f'/api/me/sponsorships/{sid}/parcelas','user_a'),('GET',f'/api/me/sponsorships/{sid}/parcelas','user_a'),[200,200],hold=aid)
 pp=parts(sid);assert len(pp)==2 and len({p['numero'] for p in pp})==2
 pending=next(p['id'] for p in pp if p['status']=='PENDENTE')
 race('cancelar simultaneamente ao recebimento',('PATCH',f'/api/me/sponsorships/{sid}/cancelar','user_a'),('PATCH',f'/api/ong/sponsorships/{sid}/parcelas/{pending}/confirmar','admin_a'),[200,200])
 assert own(sid)['status']=='ENCERRADO';assert len(parts(sid))==2
 call('cancelamento repetido','PATCH',f'/api/me/sponsorships/{sid}/cancelar',409,'user_a')
 one=sponsor(aid,'UNICO');oid=one['id'];op=one['primeiraParcela']['id'];call('encerramento administrativo','PATCH',f'/api/ong/sponsorships/{oid}/encerrar',role='admin_a',body=dict(justificativa='Acordo sintético'));confirm(oid,op)
 # Formal adoption: approval leaves sponsor active, delivery closes it.
 aa=animal();sa=sponsor(aa);ad=call('solicitar adoção','POST','/api/adoption-requests',201,'user_b',dict(animalId=aa))['id']
 call('aprovar adoção','PATCH',f'/api/ong/adoption-requests/{ad}/aprovar',role='admin_a');assert own(sa['id'])['status']=='ATIVO'
 call('concluir entrega','POST',f'/api/ong/adoption-requests/{ad}/confirmar-entrega',role='admin_a',body=dict(entregaConfirmada=True));assert own(sa['id'])['motivoEncerramento']=='ADOCAO_CONCLUIDA'
 # Real rollback of death AND delivery when monthly generation exceeds bounded horizon.
 dead=animal();sd=sponsor(dead);ds=sd['id'];sql(f"UPDATE apadrinhamento SET data_inicio='2000-01-01' WHERE id={ds};")
 current=call('versão animal','GET',f'/api/ong/animais/{dead}',role='admin_a')
 call('falecimento com limite reverte tudo','PATCH',f'/api/ong/animais/{dead}/situacao',409,'admin_a',dict(situacao='FALECIDO',versao=current['versao']))
 assert sql(f'SELECT situacao FROM animal WHERE id={dead};')=='DISPONIVEL'
 ar=call('solicitar para testar rollback','POST','/api/adoption-requests',201,'user_b',dict(animalId=dead))['id'];call('aprovar para rollback','PATCH',f'/api/ong/adoption-requests/{ar}/aprovar',role='admin_a')
 call('entrega com limite reverte tudo','POST',f'/api/ong/adoption-requests/{ar}/confirmar-entrega',409,'admin_a',dict(entregaConfirmada=True))
 assert sql(f'SELECT status FROM solicitacao_adocao WHERE id={ar};')=='APROVADA'
 assert sql(f'SELECT COUNT(*) FROM adocao WHERE solicitacao_id={ar};')=='0'
 assert sql(f'SELECT status FROM apadrinhamento WHERE id={ds};')=='ATIVO'
 call('liberar reserva após rollback','PATCH',f'/api/ong/adoption-requests/{ar}/cancelar-aprovacao',role='admin_a',body=dict(justificativa='Encerrar teste sintético'))
 sql(f"UPDATE apadrinhamento SET data_inicio=CURRENT_DATE WHERE id={ds};")
 current=call('versão preservada','GET',f'/api/ong/animais/{dead}',role='admin_a');call('falecimento formal','PATCH',f'/api/ong/animais/{dead}/situacao',role='admin_a',body=dict(situacao='FALECIDO',versao=current['versao']))
 assert own(ds)['motivoEncerramento']=='FALECIMENTO_ANIMAL';confirm(ds,sd['primeiraParcela']['id'])
 # Force a concrete ledger failure in this fixture only: confirmation must roll back.
 fail_animal=animal();failure=sponsor(fail_animal);fs=failure['id'];fp=failure['primeiraParcela']['id']
 sql(f"DELIMITER $$\nCREATE TRIGGER k81_receita_falha BEFORE INSERT ON movimentacao_financeira FOR EACH ROW BEGIN IF NEW.parcela_apadrinhamento_id={fp} THEN SIGNAL SQLSTATE '23000' SET MESSAGE_TEXT='K81 fixture rollback'; END IF; END$$\nDELIMITER ;\n")
 try:
  confirm(fs,fp,409)
  assert sql(f'SELECT status FROM parcela_apadrinhamento WHERE id={fp};')=='PENDENTE'
  assert sql(f'SELECT COUNT(*) FROM movimentacao_financeira WHERE parcela_apadrinhamento_id={fp};')=='0'
 finally:sql('DROP TRIGGER k81_receita_falha;')
 # Donations + realized expense reuse the same ledger.
 donation=call('doação K6','POST','/api/donations',201,'user_a',dict(ongId=8101,valor='10.10'))['id'];call('confirmar doação K6','PATCH',f'/api/ong/donations/{donation}/confirmar',role='admin_a')
 expense=call('despesa K6','POST','/api/ong/finance/movimentacoes',201,'admin_a',dict(tipo='DESPESA',categoria='ALIMENTACAO',descricao='Despesa sintética',valor='3.20'))['id']
 call('aprovar despesa','PATCH',f'/api/ong/finance/movimentacoes/{expense}/aprovar',role='admin_a');call('pagar despesa','PATCH',f'/api/ong/finance/movimentacoes/{expense}/confirmar-pagamento',role='admin_a')
 report=call('relatório consolidado','GET','/api/ong/relatorios/financeiro',role='admin_a');assert Decimal(report['receitasPorCategoria']['APADRINHAMENTO'])==Decimal(baseline_report['receitasPorCategoria'].get('APADRINHAMENTO','0'))+Decimal('102.00') and Decimal(report['saldoRealizado'])==Decimal(baseline['saldoRealizado'])+Decimal('108.90')
 call('voluntário sem relatório','GET','/api/ong/relatorios/financeiro',403,'volunteer')
 payload=dict(titulo='Demonstrativo K81',descricao='Dados sintéticos',periodoInicio='2020-01-01',periodoFim='9999-12-31')
 demo=call('criar demonstrativo','POST','/api/ong/transparencia',201,'admin_a',payload);did=ids['demonstrativo']=demo['id'];public=f'/api/public/ongs/8101/transparencia/{did}';internal=f'/api/ong/transparencia/{did}'
 call('rascunho não é público','GET',public,404)
 for role in ['volunteer','vet','platform','user_a']:call('publicação indevida '+role,'PATCH',internal+'/publicar',403,role)
 call('outra ONG não publica','PATCH',internal+'/publicar',404,'admin_b')
 published=call('publicar snapshot','PATCH',internal+'/publicar',role='admin_a');p=call('consulta pública','GET',public)
 assert set(p)=={'id','ong','titulo','descricao','periodoInicio','periodoFim','publicadoEm','dados'}
 assert set(p['dados'])=={'receitasRealizadas','despesasRealizadas','resultadoLiquido','receitasPorCategoria','despesasPorCategoria','quantidadeMovimentacoes'}
 assert Decimal(p['dados']['receitasRealizadas'])==Decimal(baseline['receitasRealizadas'])+Decimal('112.10') and Decimal(p['dados']['despesasRealizadas'])==Decimal(baseline['despesasRealizadas'])+Decimal('3.20')
 call('listagem pública','GET','/api/public/ongs/8101/transparencia');call('consulta interna voluntário','GET',internal,role='volunteer')
 call('publicado não permite editar','PATCH',internal,409,'admin_a',dict(titulo='Outro'))
 call('publicação repetida','PATCH',internal+'/publicar',409,'admin_a')
 # A later income must not mutate frozen public values.
 extra=call('outra receita','POST','/api/ong/finance/movimentacoes',201,'admin_a',dict(tipo='RECEITA',categoria='OUTRA_RECEITA',descricao='Receita sintética',valor='0.23'))['id'];call('confirmar receita','PATCH',f'/api/ong/finance/movimentacoes/{extra}/confirmar-recebimento',role='admin_a')
 assert call('snapshot congelado','GET',public)['dados']==p['dados']
 call('despublicar','PATCH',internal+'/despublicar',role='admin_a');call('despublicado oculto','GET',public,404)
 edited=call('editar título não recalcula','PATCH',internal,role='admin_a',body=dict(titulo='Revisado'));assert edited['dados']==p['dados']
 call('republicar recalcula','PATCH',internal+'/publicar',role='admin_a');assert Decimal(call('republicado público','GET',public)['dados']['receitasRealizadas'])==Decimal(baseline['receitasRealizadas'])+Decimal('112.33')
 unicode=call('persistir 120 emoji','POST','/api/ong/transparencia',201,'admin_a',dict(payload,titulo='🐾'*120));assert sql(f"SELECT CHAR_LENGTH(titulo) FROM demonstrativo_transparencia WHERE id={unicode['id']};")=='120'
 call('período repetido permitido','POST','/api/ong/transparencia',201,'admin_a',payload)
 schema=sql('SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;');assert len(schema.splitlines())==13 and '13\t1' in schema
 result=dict(verdict='PASS',database='patas_k81',mysql=sql('SELECT VERSION();'),port=13319,apiPort=18084,migrations=schema.splitlines(),ids=ids,requests=rows)
except Exception as e:
 result=dict(verdict='FAIL',error=str(e),ids=ids,requests=rows)
 raise
finally:
 name='creation-race-before.json' if os.environ.get('K81_RACE_ONLY') else 'mysql-http.json'
 (OUT/name).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(dict(verdict=result['verdict'],checks=len(rows))))
