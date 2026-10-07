"""Fresh immutable QA-only snapshot for the bulk recipe strategy request."""
from pathlib import Path
p=Path(__file__).with_name('qa_recipe_menu_preservation.py')
source=p.read_text(encoding='utf-8').replace(
    "OUT=ROOT/'app/build/recipe-menu-completion'",
    "OUT=ROOT/'app/build/recipe-linkage-strategy'").replace(
    'healthcare-recipe-menu-completion-qa-restore.tar',
    'healthcare-recipe-linkage-strategy-qa-restore.tar')
exec(compile(source,str(p),'exec'))
