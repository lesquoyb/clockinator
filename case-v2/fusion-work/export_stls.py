import adsk.core, adsk.fusion, os, json


def run(_context: str):
    d=adsk.fusion.Design.cast(adsk.core.Application.get().activeProduct)
    occurrence=next(o for o in d.rootComponent.occurrences if o.component.name=='Preparation export impression')
    directory=r'C:\Users\baptiste\Documents\GitHub\clockinator\case-v2\output'
    results=[]
    assert occurrence.component.bRepBodies.count==14
    for body in occurrence.component.bRepBodies:
        path=os.path.join(directory,body.name+'.stl')
        options=d.exportManager.createSTLExportOptions(body,path)
        options.sendToPrintUtility=False
        options.isBinaryFormat=True
        options.unitType=adsk.fusion.DistanceUnits.MillimeterDistanceUnits
        options.meshRefinement=adsk.fusion.MeshRefinementSettings.MeshRefinementHigh
        assert d.exportManager.execute(options)
        assert os.path.isfile(path) and os.path.getsize(path)>84,path
        results.append({'file':body.name+'.stl','bytes':os.path.getsize(path)})
    occurrence.deleteMe()
    print(json.dumps(results))
