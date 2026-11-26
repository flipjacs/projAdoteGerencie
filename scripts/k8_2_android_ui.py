#!/usr/bin/env python3
"""ADB helpers for the real K8.2 APK. Logs never include credential fields.
Uses only the active emulator and private synthetic K8.2 fixture accounts.
"""
import json,pathlib,re,shlex,subprocess,sys,time,xml.etree.ElementTree as E
ADB='/home/flipjacs/Android/Sdk/platform-tools/adb'
ROOT=pathlib.Path(__file__).resolve().parents[1]
def adb(*args):return subprocess.check_output([ADB,'-s','emulator-5554',*args],stderr=subprocess.DEVNULL)
def nodes():
 path=f'/sdcard/k82-{time.time_ns()}.xml'
 result=adb('shell','uiautomator','dump','--compressed',path)
 assert b'dumped to:' in result,'Não foi possível obter estado novo do AVD'
 return list(E.fromstring(adb('shell','cat',path)).iter('node'))
def tap(n):
 b=list(map(int,re.findall(r'\d+',n.get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
def click(text):
 ns=[n for n in nodes() if n.get('text')==text or n.get('content-desc')==text]
 if not ns:raise AssertionError('Controle não encontrado: '+text)
 tap(ns[0])
def seek(text,click_it=False,limit=12):
 for _ in range(limit):
  state=nodes()
  if any(n.get('text') in ['Atualizar dados','Tentar novamente'] for n in state) and text not in ['Atualizar dados','Tentar novamente']:
   retry=next(n for n in state if n.get('text') in ['Atualizar dados','Tentar novamente']);tap(retry);time.sleep(.5);continue
  ns=[n for n in state if n.get('text')==text or n.get('content-desc')==text]
  if ns:
   if click_it:tap(ns[0])
   return
  adb('shell','input','swipe','390','1370','390','620','350')
 raise AssertionError('Controle não encontrado após rolagem: '+text)
def shot(name):
 path=ROOT/'artifacts/k8.2/android'/name;path.parent.mkdir(parents=True,exist_ok=True);assert not path.exists(),'Preserve a captura original; use outro nome';path.write_bytes(adb('exec-out','screencap','-p'));return path
def fill(index,value):
 ns=[n for n in nodes() if n.get('class')=='android.widget.EditText'];tap(ns[index]);adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','keycombination','KEYCODE_CTRL_LEFT','KEYCODE_A');adb('shell','input','keyevent','KEYCODE_DEL');adb('shell','input','text',shlex.quote(value.replace(' ','%s')))
def credential(role,field,index):
 p=pathlib.Path(pathlib.Path('/tmp/patas-k81-current').read_text().strip());value=json.loads((p/'k82-accounts.json').read_text())[role][field];fill(index,value)
if __name__=='__main__':
 args=sys.argv[1:];action=args[0]
 if action in ('expect','absent'):
  present=any(n.get('text')==args[1] for n in nodes());ok=present if action=='expect' else not present
  f=ROOT/'artifacts/k8.2/android-checks.json';data=json.loads(f.read_text()) if f.exists() else [];data.append(dict(check=args[2] if len(args)>2 else args[1],passed=ok));f.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n');assert ok,'Estado de tela divergente';print('Estado verificado.')
 elif action=='dump':
  for n in nodes():
   if n.get('class')=='android.widget.EditText':print('FIELD',n.get('bounds'))
   elif n.get('text'):print(n.get('text'),n.get('bounds'))
 elif action=='click':click(args[1])
 elif action=='seek':seek(args[1],len(args)>2 and args[2]=='click')
 elif action=='shot':print(shot(args[1]))
 elif action=='fill':fill(int(args[1]),args[2])
 elif action=='credential':credential(args[1],args[2],int(args[3]));print('Campo privado preenchido.')
 elif action=='back':adb('shell','input','keyevent','4')
 elif action=='scroll':adb('shell','input','swipe','390','1370','390','620','350')
 elif action=='up':adb('shell','input','swipe','390','620','390','1370','350')
 elif action=='login':
  credential(args[1],'email',0);credential(args[1],'senha',1);adb('shell','input','keyevent','4');time.sleep(.3);click('Entrar');print('Login enviado sem registrar credenciais.')
