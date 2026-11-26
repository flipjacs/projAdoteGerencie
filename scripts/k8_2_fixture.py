#!/usr/bin/env python3
"""Seed only the existing K8.1 isolated MySQL with synthetic K8.2 mobile data.
Credentials are generated and kept in its private /tmp directory, never stdout.
"""
import json,pathlib,secrets,subprocess,urllib.request,urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[1]
PRIVATE=pathlib.Path(pathlib.Path('/tmp/patas-k81-current').read_text().strip())
ENV=dict(line.split('=',1) for line in (PRIVATE/'mysql.env').read_text().splitlines())
assert ENV['MYSQL_DATABASE']=='patas_k81'
def sql(query):
 p=subprocess.run(['docker','exec','-i','-e','MYSQL_PWD='+ENV['MYSQL_ROOT_PASSWORD'],'patas-k81-isolado','mysql','-uroot','-N','--default-character-set=utf8mb4','patas_k81'],input=query,text=True,capture_output=True)
 assert p.returncode==0,'Falha na fixture isolada'
 return p.stdout.strip()
def call(method,path,body=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 r=urllib.request.Request('http://127.0.0.1:18084'+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
 with urllib.request.urlopen(r,timeout=20) as res:return json.load(res)
assert sql('SELECT DATABASE();')=='patas_k81'
assert sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;')=='13'
if (PRIVATE/'k82-fixture.json').exists():print('Fixture K8.2 existente preservada.');raise SystemExit(0)
assert sql('SELECT COUNT(*) FROM ong WHERE id=8201;')=='0'
sql("INSERT INTO ong(id,nome,email,cidade,estado) VALUES(8201,'Amigos da Floresta','ong@k82.invalid','Manaus','AM');")
accounts={}
for i,(role,profile,name) in enumerate([('admin','administrador','Marina'),('volunteer','voluntario','Joana'),('vet','veterinario','Rita')],8201):
 password=secrets.token_urlsafe(24);hashed=subprocess.run(['php','-r','echo password_hash(stream_get_contents(STDIN),PASSWORD_BCRYPT);'],input=password,text=True,capture_output=True,check=True).stdout
 email=role+'@k82.invalid';sql(f"INSERT INTO usuario(id,ong_id,nome,email,senha_hash,perfil,ativo) VALUES({i},8201,'{name}','{email}','{hashed}','{profile}',true);")
 accounts[role]=dict(email=email,senha=password)
for role,name in [('user','Ana'),('other','Bruno')]:
 account=dict(email=role+'@k82.invalid',senha=secrets.token_urlsafe(24));call('POST','/api/usuarios/cadastro',dict(nome=name,**account));accounts[role]=account
accounts['platform']=json.loads((PRIVATE/'accounts.json').read_text())['platform']
(PRIVATE/'k82-accounts.json').write_text(json.dumps(accounts));(PRIVATE/'k82-accounts.json').chmod(0o600)
tokens={r:call('POST','/api/auth/login',a)['accessToken'] for r,a in accounts.items()}
def animal(name):
 a=call('POST','/api/animais',dict(nome=name,especie='GATO',sexo='FEMEA',porte='PEQUENO',dataEntrada='2026-01-01',descricao='Uma companhia tranquila, à espera de cuidado e de um novo lar.'),tokens['admin'])
 call('PATCH',f"/api/ong/animais/{a['id']}/situacao",dict(situacao='DISPONIVEL',versao=a['versao']),tokens['admin']);call('PATCH',f"/api/ong/animais/{a['id']}/publicar",token=tokens['admin']);return a['id']
closed=animal('Estrela')
s=call('POST','/api/sponsorships',dict(animalId=closed,modalidade='MENSAL',valor='25.50'),tokens['user'])
a=call('GET',f'/api/ong/animais/{closed}',token=tokens['admin']);call('PATCH',f'/api/ong/animais/{closed}/situacao',dict(situacao='FALECIDO',versao=a['versao']),tokens['admin'])
late=animal('Sol');old=call('POST','/api/sponsorships',dict(animalId=late,modalidade='MENSAL',valor='15.00'),tokens['user'])
sql(f"UPDATE apadrinhamento SET data_inicio=DATE_SUB(CURRENT_DATE,INTERVAL 2 MONTH) WHERE id={old['id']}; UPDATE parcela_apadrinhamento SET vencimento=DATE_SUB(CURRENT_DATE,INTERVAL 2 MONTH) WHERE apadrinhamento_id={old['id']};")
lua=animal('Lua')
clinical=call('POST',f'/api/ong/animais/{lua}/prontuarios',dict(tipo='CONSULTA',titulo='Avaliação inicial',descricao='Animal em boas condições para o acolhimento.',dataProcedimento='2026-01-01'),tokens['vet'])
ids=dict(ongId=8201,lua=lua,overdueSponsor=old['id'],closedSponsor=s['id'],closedAnimal=closed,clinical=clinical['id'])
(PRIVATE/'k82-fixture.json').write_text(json.dumps(ids));(ROOT/'artifacts/k8.2/fixture.json').write_text(json.dumps(dict(database='patas_k81',apiPort=18084,ids=ids),indent=2)+'\n')
print('Fixture sintética K8.2 pronta; nenhuma credencial registrada.')
