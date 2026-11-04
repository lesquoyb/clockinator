import adsk.fusion, sys, os, json


def run(_context: str):
    state=sys.modules['clockinator_dock_renders']
    print(json.dumps([{'name':name,'state':future.renderState,'progress':future.progress,'exists':os.path.isfile(path),'bytes':os.path.getsize(path) if os.path.isfile(path) else 0} for name,path,future in state.jobs]))
