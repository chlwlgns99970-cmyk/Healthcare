"""Exact QA-only snapshot, restoration and verification for this request."""
from pathlib import Path
import importlib.util, json, sys, contextlib, io
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/build/recipe-full-reference'
OUT.mkdir(exist_ok=True)
spec=importlib.util.spec_from_file_location('qa_helper',ROOT/'app/build/release-1.0.6/restore_verify_qa.py')
helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
helper.OUT=OUT;helper.BASELINE=OUT/'qa-before.tar';helper.PACKAGE_BASELINE=OUT/'package-before.json'
helper.REMOTE_BACKUP='/data/local/tmp/healthcare-recipe-full-reference-qa-restore.tar'
phase=sys.argv[1]
if phase=='capture':
    assert not helper.BASELINE.exists(), 'Immutable baseline already exists'
    assert helper.text(helper.adb(['get-serialno']))==helper.SERIAL
    helper.adb(['shell','am','force-stop',helper.QA]);helper.assert_qa_private_cwd()
    data=helper.adb(['exec-out','run-as',helper.QA,'tar','-cf','-','databases','shared_prefs','files']).stdout
    files=helper.read_tar(data,require_captured_wal=True)
    assert helper.sqlite_snapshots(files)[helper.DB_NAME]['version']==8
    helper.BASELINE.write_bytes(data)
    identities={name:helper.package_identity(name) for name in (helper.PRODUCT,helper.QA)}
    helper.PACKAGE_BASELINE.write_text(json.dumps(identities,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'capturedBytes':len(data),'privateFiles':len(files),'DBVersion':8}))
else:
    with contextlib.redirect_stdout(io.StringIO()):
        helper.main()
    reportPath=OUT/f'preservation-{phase}.json'
    report=json.loads(reportPath.read_text(encoding='utf-8'))
    baseline=json.loads(helper.PACKAGE_BASELINE.read_text(encoding='utf-8'))
    first=lambda values: [x for x in values if x.startswith('firstInstallTime=')]
    report['qaFirstInstallPreserved']=first(report['qaIdentityAfter'])==first(baseline[helper.QA])
    assert report['qaFirstInstallPreserved'], 'QA first install time changed'
    reportPath.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({key:report[key] for key in ('status','databaseVersions','changedTables',
        'allAllowlistedPrivateFileBytesIdentical','productIdentityUnchanged','qaFirstInstallPreserved')},ensure_ascii=False))

