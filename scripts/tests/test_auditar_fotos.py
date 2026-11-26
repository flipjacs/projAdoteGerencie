import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'auditar_fotos.py'
A = '11111111-1111-4111-8111-111111111111'
B = '22222222-2222-4222-8222-222222222222'
C = '33333333-3333-4333-8333-333333333333'

class AuditoriaTest(unittest.TestCase):
 def run_audit(self, base, rows, stopped=True):
  root=Path(base); photos=root/'fotos'; photos.mkdir(exist_ok=True)
  inventory=root/'inventario.tsv'
  with inventory.open('w',newline='',encoding='utf-8') as f:
   w=csv.writer(f,delimiter='\t'); w.writerow(['foto_id','animal_id','arquivo','principal','animal_existe']); w.writerows(rows)
  args=[sys.executable,str(SCRIPT),'--diretorio',str(photos),'--inventario',str(inventory)]
  if stopped: args.append('--escritas-interrompidas')
  return subprocess.run(args,text=True,capture_output=True)
 def test_inventario_identifica_sem_apagar(self):
  with tempfile.TemporaryDirectory() as tmp:
   p=Path(tmp)/'fotos';p.mkdir();(p/A).write_bytes(b'a');(p/C).write_bytes(b'c')
   r=self.run_audit(tmp,[[1,10,A,1,1],[2,10,B,1,1]])
   self.assertEqual(0,r.returncode,r.stderr); result=json.loads(r.stdout); types={i['tipo'] for i in result['inconsistencias']}
   self.assertTrue({'ARQUIVO_SEM_METADADO','ARQUIVO_AUSENTE','MULTIPLAS_PRINCIPAIS'} <= types)
   self.assertTrue((p/A).exists());self.assertTrue((p/C).exists());self.assertNotIn(tmp,r.stdout)
 def test_link_e_nome_inseguro_nao_sao_seguidos(self):
  with tempfile.TemporaryDirectory() as tmp:
   p=Path(tmp)/'fotos';p.mkdir();target=Path(tmp)/'privado';target.write_text('segredo');(p/A).symlink_to(target)
   r=self.run_audit(tmp,[[1,10,A,1,1],[2,10,'../../privado',0,1]])
   self.assertEqual(0,r.returncode,r.stderr); self.assertIn('LINK_SIMBOLICO',r.stdout);self.assertIn('NOME_INVALIDO',r.stdout);self.assertNotIn('segredo',r.stdout);self.assertNotIn('../../privado',r.stdout)
 def test_exige_interrupcao_de_escritas(self):
  with tempfile.TemporaryDirectory() as tmp:self.assertNotEqual(0,self.run_audit(tmp,[],False).returncode)
 def test_principal_ausente_e_animal_inexistente(self):
  with tempfile.TemporaryDirectory() as tmp:
   p=Path(tmp)/'fotos';p.mkdir();(p/A).touch()
   r=self.run_audit(tmp,[[1,10,A,0,0]])
   self.assertEqual(0,r.returncode,r.stderr);self.assertIn('SEM_PRINCIPAL',r.stdout);self.assertIn('ANIMAL_INEXISTENTE',r.stdout)

 def test_identificadores_duplicados_sao_rejeitados(self):
  with tempfile.TemporaryDirectory() as tmp:
   result=self.run_audit(tmp,[[1,10,A,1,1],[1,10,B,0,1]])
   self.assertNotEqual(0,result.returncode);self.assertEqual('',result.stdout)
 def test_raiz_simbolica_e_rejeitada(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);(root/'real').mkdir();(root/'fotos').symlink_to(root/'real',target_is_directory=True)
   result=self.run_audit(tmp,[])
   self.assertNotEqual(0,result.returncode);self.assertEqual('',result.stdout)

if __name__=='__main__': unittest.main()
