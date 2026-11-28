#!/usr/bin/env python3
"""Prints the next batch of cards to verify: python3 tbld-pool/next_batch.py <U|G|R|W|C|M> [count=50]

Scripted but unverified cards with a picture, alphabetical. Colour = the cost's colours
(M = two or more colours, C = no coloured mana in the cost, other = exactly that colour)."""
import os
import re
import subprocess
import sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
want = sys.argv[1]
count = int(sys.argv[2]) if len(sys.argv) > 2 else 50
listing = subprocess.run(['java', 'tbld-pool/TbldPool.java', 'verify', '--list', 'scripted'], cwd=root,
                         capture_output=True, text=True).stdout.split('\n')
scripted = [l.split('\t', 1)[1].strip() for l in listing if l.startswith('scripted') and '\t' in l]
verified = {l.strip() for l in open(root + '/tbld-pool/verified.txt', encoding='utf-8') if l.strip() and not l.startswith('#')}
cards, cur = {}, None
for l in open(root + '/forge-gui/res/tbld/pool/cards.txt', encoding='utf-8'):
    l = l.rstrip('\n')
    m = re.match(r'^\[(.*)\]$', l)
    if m:
        cur = m.group(1)
        cards[cur] = {}
    elif cur and '=' in l:
        k, v = l.split('=', 1)
        cards[cur][k] = v
pics = {os.path.splitext(f)[0][5:] for f in os.listdir(root + '/forge-gui/res/tbld/pics') if f.startswith('TBLD ')}
out = []
for n in sorted(scripted):
    if n in verified or (n not in pics and n.replace(' // ', '_') not in pics):
        continue
    cols = {c for c in cards.get(n, {}).get('cost', '') if c in 'WUBRG'}
    if (want == 'M' and len(cols) < 2) or (want == 'C' and cols) or (want not in 'MC' and cols != {want}):
        continue
    out.append(n)
print('\n'.join(out[:count]))
print('# %d left in total' % len(out), file=sys.stderr)
