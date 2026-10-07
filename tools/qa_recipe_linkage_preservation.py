"""Immutable QA snapshot for recipe linkage maximization."""
from pathlib import Path
p = Path(__file__).with_name('qa_recipe_menu_preservation.py')
source = p.read_text(encoding='utf-8').replace(
    "OUT=ROOT/'app/build/recipe-menu-completion'",
    "OUT=ROOT/'app/build/recipe-linkage-maximization'").replace(
    'healthcare-recipe-menu-completion-qa-restore.tar',
    'healthcare-recipe-linkage-maximization-qa-restore.tar')
exec(compile(source, str(p), 'exec'))
