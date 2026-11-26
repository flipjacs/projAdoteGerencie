#!/usr/bin/env python3
"""Independent persistence probes for APK flows, restricted to K8.2 fixture.
Does not claim the probe requests were made by the APK. Secrets stay private.
"""
import json,pathlib,subprocess,urllib.request,urllib.error,sys,time
ROOT=pathlib.Path(__file__).resolve().parents[1];PRIVATE=pathlib.Path(pathlib.Path('/tmp/patas-k81-current').read_text().strip())
ACCOUNTS=json.loads((PRIVATE/'k82-accounts.json').read_text());FIXTURE=json.loads((PRIVATE/'k82-fixture.json').read_text())
BASE='http://127.0.0.1:18084'
def request(method,path,body=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
 try:
  with urllib.request.urlopen(req,timeout=20) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.load(e)
def call(role,method,path,body=None):
 assert role in ACCOUNTS
 store=PRIVATE/'k82-probe-tokens.json';cache=json.loads(store.read_text()) if store.exists() else {}
 if role not in cache or cache[role]['until']<time.time():
  status,j=request('POST','/api/auth/login',ACCOUNTS[role]);assert status==200;cache[role]=dict(token=j['accessToken'],until=time.time()+1800);store.write_text(json.dumps(cache));store.chmod(0o600)
 return request(method,path,body,cache[role]['token'])
def sql(query):
 env=dict(l.split('=',1) for l in (PRIVATE/'mysql.env').read_text().splitlines());assert env['MYSQL_DATABASE']=='patas_k81'
 p=subprocess.run(['docker','exec','-i','-e','MYSQL_PWD='+env['MYSQL_ROOT_PASSWORD'],'patas-k81-isolado','mysql','-uroot','-N','patas_k81'],input=query,capture_output=True,text=True);assert p.returncode==0;return p.stdout.strip()
def state():
 status,records=call('user','GET','/api/me/sponsorships');assert status==200
 current=[x for x in records['content'] if x['animal']['id']==FIXTURE['lua']];assert current
 sponsor=current[0];sid=sponsor['id'];status,parts=call('user','GET',f'/api/me/sponsorships/{sid}/parcelas');assert status==200
 _,summary=call('admin','GET','/api/ong/finance/resumo')
 return dict(sponsorId=sid,sponsorStatus=sponsor['status'],parts=[{k:p[k] for k in ['id','numero','status','valor']} for p in parts['content']],summary=summary,revenueCount=int(sql(f'SELECT COUNT(*) FROM movimentacao_financeira WHERE ong_id=8201 AND parcela_apadrinhamento_id IN (SELECT id FROM parcela_apadrinhamento WHERE apadrinhamento_id={sid});')))
if __name__=='__main__':
 action=sys.argv[1]
 if action=='state':
  r=state();path=ROOT/'artifacts/k8.2/real-flow-probes.json';data=json.loads(path.read_text()) if path.exists() else [];data.append(dict(step=sys.argv[2],source='Independent HTTP/MySQL probe after APK action',**r));path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n');print(json.dumps(r))
 elif action=='race-confirm':
  sid=int(sys.argv[2]);pid=int(sys.argv[3]);status,_=call('admin','PATCH',f'/api/ong/sponsorships/{sid}/parcelas/{pid}/confirmar');assert status==200;print('Recebimento externo confirmado para reproduzir409 na tela obsoleta.')
 elif action=='admin-role':
  assert sys.argv[2] in ['administrador','voluntario'];assert sql("SELECT email FROM usuario WHERE id=8201 AND ong_id=8201;")=='admin@k82.invalid';sql(f"UPDATE usuario SET perfil='{sys.argv[2]}' WHERE id=8201 AND ong_id=8201;");print('Perfil da conta de teste isolada atualizado.')
