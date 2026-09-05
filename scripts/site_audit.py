#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re, sys
from pathlib import Path


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--site',type=Path,required=True); args=ap.parse_args(); site=args.site
    errors=[]
    required=['index.html','compatibility.html','assets/css/styles.css','assets/js/cards.js','assets/js/home.js','assets/js/compatibility.js','data/site-index.json','sitemap.xml','robots.txt','ads.txt']
    for item in required:
        if not (site/item).is_file(): errors.append(f'missing {item}')
    if (site/'data/site-index.json').is_file():
        data=json.loads((site/'data/site-index.json').read_text())
        for game in data.get('games',[]):
            cusa=game.get('cusaId','')
            if not re.fullmatch(r'CUSA\d{4,6}',cusa): errors.append(f'invalid CUSA id: {cusa}')
            if not (site/'games'/cusa/'index.html').is_file(): errors.append(f'missing game page for {cusa}')
    for path in site.rglob('*.html'):
        text=path.read_text(encoding='utf-8')
        forbidden=['<dialog','View permanent page','Open game discussion','Archived discussion','Permanent game pages','compatibility.html?game=']
        for needle in forbidden:
            if needle.lower() in text.lower(): errors.append(f'{path.relative_to(site)} contains forbidden legacy UI: {needle}')
    if errors:
        print('Site audit failed:')
        for e in errors: print('-',e)
        sys.exit(1)
    print('Site audit passed')

if __name__=='__main__': main()
