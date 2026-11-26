#!/usr/bin/env python3
"""Auditoria offline: recebe inventário TSV e diretório, nunca altera dados."""
import argparse
import csv
import json
import os
import re
import stat
import sys
from collections import defaultdict

UUID = re.compile(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
COLUMNS = ['foto_id', 'animal_id', 'arquivo', 'principal', 'animal_existe']

def audit(directory, inventory):
    # O_NOFOLLOW impede que a raiz configurada seja trocada por um link simbólico.
    fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        disk = {}
        with os.scandir(fd) as entries:
            for entry in entries:
                mode = entry.stat(follow_symlinks=False).st_mode
                disk[entry.name] = 'link' if stat.S_ISLNK(mode) else 'arquivo' if stat.S_ISREG(mode) else 'outro'
        findings = []
        def add(kind, severity, action, animal=None, photo=None, filename=None):
            findings.append({'tipo': kind, 'gravidade': severity, 'acao_recomendada': action,
                             'animal_id': animal, 'foto_id': photo, 'arquivo': filename})
        referenced = set()
        principals = defaultdict(int)
        counts = defaultdict(int)
        ids = set()
        with open(inventory, encoding='utf-8', newline='') as source:
            reader = csv.DictReader(source, delimiter='\t')
            if reader.fieldnames != COLUMNS:
                raise ValueError('Cabeçalho TSV inválido')
            for row in reader:
                if None in row or any(row.get(k) is None for k in COLUMNS):
                    raise ValueError('Linha TSV inválida')
                photo = int(row['foto_id']); animal = int(row['animal_id'])
                if photo <= 0 or animal <= 0 or photo in ids or row['principal'] not in ('0','1') or row['animal_existe'] not in ('0','1'):
                    raise ValueError('Dados de inventário inválidos')
                ids.add(photo); counts[animal] += 1; principals[animal] += int(row['principal'])
                name = row['arquivo']
                if row['animal_existe'] == '0':
                    add('ANIMAL_INEXISTENTE','ALTO','Investigar integridade referencial.',animal,photo)
                if not UUID.fullmatch(name):
                    add('NOME_INVALIDO','ALTO','Investigar metadado sem usar o caminho informado.',animal,photo,'(inválido)')
                    continue
                referenced.add(name)
                kind = disk.get(name)
                if kind is None: add('ARQUIVO_AUSENTE','ALTO','Restaurar arquivo de backup compatível.',animal,photo,name)
                elif kind == 'link': add('LINK_SIMBOLICO','ALTO','Investigar sem seguir o link.',animal,photo,name)
                elif kind != 'arquivo': add('TIPO_INESPERADO','ALTO','Investigar item do armazenamento.',animal,photo,name)
        for animal,count in counts.items():
            if principals[animal] == 0: add('SEM_PRINCIPAL','MEDIO','Investigar seleção da foto principal.',animal)
            elif principals[animal] > 1: add('MULTIPLAS_PRINCIPAIS','ALTO','Revisar principais; não corrigir automaticamente.',animal)
            if count > 10: add('LIMITE_EXCEDIDO','MEDIO','Investigar a quantidade de fotos.',animal)
        for name,kind in disk.items():
            if name not in referenced:
                safe = name if UUID.fullmatch(name) else '(nome fora do padrão)'
                add('ARQUIVO_SEM_METADADO' if kind == 'arquivo' else 'ITEM_NAO_REFERENCIADO',
                    'MEDIO','Investigar e preservar antes de qualquer remoção.',filename=safe)
        return {'modo':'auditoria', 'escritas_interrompidas_declaradas':True, 'fotos_no_inventario':len(ids),
                'itens_no_diretorio':len(disk), 'inconsistencias':findings}
    finally:
        os.close(fd)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--diretorio',required=True)
    parser.add_argument('--inventario',required=True)
    parser.add_argument('--escritas-interrompidas',action='store_true',required=True,
                        help='Confirma que uploads e alterações estão interrompidos durante a exportação e auditoria.')
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.diretorio,args.inventario),ensure_ascii=False,indent=2))
    except (OSError, ValueError, csv.Error) as exc:
        # Sem caminhos ou conteúdo possivelmente sensível na mensagem.
        print('Não foi possível concluir a auditoria: verifique diretório e inventário.',file=sys.stderr)
        return 2
    return 0

if __name__ == '__main__':
    sys.exit(main())
