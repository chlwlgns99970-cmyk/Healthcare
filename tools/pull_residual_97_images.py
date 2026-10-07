"""Read only explicitly named test screenshots from the QA package."""
import io
import subprocess
import tarfile
from audit_recipe_residual_97 import ROOT, OUT, load

target=ROOT/'app/build/recipe-residual-97/screenshots'
target.mkdir(exist_ok=True)
names=['cache/residual-97-'+r['selectedId']+'.png' for r in load(OUT/'newly-published-device-fixtures.json')]
names += ['cache/final-residual-'+x+'.png' for x in ['kimbap','partial-original','original-complete','official-kcal','parae-radish-survey']]
result=subprocess.run([r'C:\Users\young\AppData\Local\Android\Sdk\platform-tools\adb.exe','-s','R5KL20HFPAK',
    'exec-out','run-as','com.example.healthcare.qa','tar','-cf','-',*names],capture_output=True,check=True)
with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
    for item in archive.getmembers():
        assert item.name in names and item.isfile()
        (target/ROOT.__class__(item.name).name).write_bytes(archive.extractfile(item).read())
print('QA-only screenshots',len(names))
