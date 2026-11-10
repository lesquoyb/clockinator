import importlib.util
import sys
from pathlib import Path
import traceback

def run(context):
    directory=Path(__file__).resolve().parent.parent
    log=directory/'RenderEinkV3'/'result.txt'
    try:
        state=sys.modules.get('clockinator_v3_renders')
        filename='render_queue.py' if state and getattr(state,'awaiting_queue',False) else 'render_v3.py'
        spec=importlib.util.spec_from_file_location('eink_job',directory/filename)
        module=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.run('')
        sys.modules['clockinator_v3_renders'].awaiting_queue = filename=='render_v3.py'
        log.write_text(filename+' SUCCESS',encoding='utf-8')
    except:
        log.write_text(traceback.format_exc(),encoding='utf-8')
        raise
