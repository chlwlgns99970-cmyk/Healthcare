"""Fresh immutable QA baseline for the full adjudication request."""
from pathlib import Path
import sys
p=Path(__file__).with_name('qa_recipe_menu_preservation.py')
source=p.read_text(encoding='utf-8').replace("OUT=ROOT/'app/build/recipe-menu-completion'", "OUT=ROOT/'app/build/full-adjudication'").replace('healthcare-recipe-menu-completion-qa-restore.tar','healthcare-full-adjudication-qa-restore.tar')
exec(compile(source,str(p),'exec'))
