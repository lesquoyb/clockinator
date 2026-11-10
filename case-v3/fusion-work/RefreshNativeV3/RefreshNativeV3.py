import adsk.core
import importlib.util
from pathlib import Path
import traceback

def run(context):
    directory = Path(__file__).resolve().parent.parent
    app = adsk.core.Application.get()
    log = directory / 'RefreshNativeV3' / 'result.txt'
    try:
        archive = directory.parent / 'output' / 'clockinator-v3-assembly.f3d'
        doc = app.importManager.importToNewDocument(app.importManager.createFusionArchiveImportOptions(str(archive)))
        doc.name = 'Clockinator v3 - updated assembly'
        for filename in ('build_v3.py', 'export_native_v3.py'):
            spec = importlib.util.spec_from_file_location('refresh_' + filename[:-3], directory / filename)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            module.run('')
            adsk.doEvents()
        log.write_text('SUCCESS', encoding='utf-8')
    except:
        log.write_text(traceback.format_exc(), encoding='utf-8')
        raise
