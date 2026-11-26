#!/usr/bin/env python3
"""Compose references at equal CSS/dp scale. Preserve original screenshots.
Only uniform scaling and external labels are applied, never screen retouching.
"""
from pathlib import Path
from PIL import Image,ImageOps,ImageDraw,ImageFont
import json
ROOT=Path(__file__).resolve().parents[1]/'artifacts/k8.2'
pairs=[('02-apadrinhar-form.png','02-sponsor-form-corrigido.png','Apadrinhar animal'),('03-meus-apoios.png','03-my-supports-final.png','Meus apadrinhamentos'),('04-apoio-parcelas.png','04-support-details.png','Detalhes e parcelas'),('05-cancelar-inline.png','05-cancel-inline-final.png','Encerramento pessoal'),('06-ong-apoios-lista.png','06-institutional-list.png','Gestão de apadrinhamentos'),('07b-ong-parcela-confirmar.png','07-confirm-inline.png','Confirmação de parcela'),('08-relatorio-financeiro.png','08-financial-report.png','Relatório financeiro'),('09-demonstrativos-lista.png','09-demonstratives-list.png','Demonstrativos'),('10-demonstrativo-criar.png','10-demonstrative-form.png','Criar demonstrativo'),('11-publicar-inline.png','11-publish-inline.png','Publicação'),('12-despublicar-inline.png','12-unpublish-inline.png','Despublicação'),('13-publico-lista.png','13-public-list-corrigido.png','Transparência pública'),('14-publico-detalhe.png','14-public-detail-corrigido.png','Demonstrativo público')]
fontpath='/usr/share/fonts/google-noto/NotoSans-Regular.ttf';font=ImageFont.truetype(fontpath,14);small=ImageFont.truetype(fontpath,11)
manifest=[]
for i,(web,android,title) in enumerate(pairs,1):
 wp=ROOT/'web'/web;ap=ROOT/'android'/android
 if not wp.exists() or not ap.exists():continue
 left=Image.open(wp).convert('RGB');right=Image.open(ap).convert('RGB')
 assert left.size==(390,844)
 height=round(right.height*390/right.width);right=right.resize((390,height),Image.Resampling.LANCZOS)
 canvas=Image.new('RGB',(808,max(left.height,right.height)+76),'#ffffff');draw=ImageDraw.Draw(canvas)
 draw.text((12,8),title,font=font,fill='#173d2b');draw.text((12,37),'WEB · React real · 390×844',font=small,fill='#203126');draw.text((414,37),'ANDROID · APK/ADB real · escala390dp',font=small,fill='#203126')
 canvas.paste(left,(8,68));canvas.paste(right,(410,68));out=ROOT/'comparacoes'/f'{i:02d}-{android}';out.parent.mkdir(exist_ok=True);canvas.save(out)
 manifest.append(dict(comparison=out.name,web=str(wp.relative_to(ROOT)),android=str(ap.relative_to(ROOT)),androidOriginal=Image.open(ap).size,scale='uniform width390; native bars preserved'))
(ROOT/'comparacoes/fontes.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print(f'{len(manifest)} comparações geradas; originais preservados.')
