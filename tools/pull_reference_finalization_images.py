"""Read an explicit QA-only cache allowlist after restoration."""
import io,subprocess,tarfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'app/build/recipe-reference-finalization/screenshots';OUT.mkdir(exist_ok=True)
ADB=r'C:\Users\young\AppData\Local\Android\Sdk\platform-tools\adb.exe'
names=['cache/reference-finalization-'+x+'.png' for x in ['MFDS-146','MFDS-217','MFDS-345','MENUZEN-D051309','KDCA-281-11299']]
names+=['cache/final-residual-'+x+'.png' for x in ['kimbap','partial-original','original-complete','official-kcal','parae-radish-survey']]
result=subprocess.run([ADB,'-s','R5KL20HFPAK','exec-out','run-as','com.example.healthcare.qa','tar','-cf','-',*names],capture_output=True,check=True)
with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
    for item in archive.getmembers():
        assert item.name in names and item.isfile()
        (OUT/Path(item.name).name).write_bytes(archive.extractfile(item).read())
print('QA screenshots:',len(names))
