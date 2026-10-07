"""Read only explicitly named QA cache captures; never read product storage."""
import io,subprocess,tarfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'app/build/recipe-final-residual/screenshots';OUT.mkdir(exist_ok=True)
ADB=r'C:\Users\young\AppData\Local\Android\Sdk\platform-tools\adb.exe'
NAMES=['MFDS-183','MFDS-278','MFDS-294','MENUZEN-D135066','MENUZEN-D082037','MENUZEN-D015004','parae-radish-survey','kimbap','official-kcal']
names=['cache/final-residual-'+x+'.png' for x in NAMES]
result=subprocess.run([ADB,'-s','R5KL20HFPAK','exec-out','run-as','com.example.healthcare.qa','tar','-cf','-',*names],capture_output=True,check=True)
with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
    for item in archive.getmembers():
        assert item.name in names and item.isfile()
        (OUT/Path(item.name).name).write_bytes(archive.extractfile(item).read())
print('QA screenshot files:',len(names))
