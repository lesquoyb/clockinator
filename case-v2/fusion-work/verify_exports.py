import collections
import json
import math
from pathlib import Path
import struct
import zipfile


directory=Path(__file__).resolve().parents[1]/'output'
results=[]
for path in sorted(directory.glob('*.stl')):
    data=path.read_bytes()
    triangles=struct.unpack_from('<I',data,80)[0]
    assert len(data)==84+50*triangles,path
    points=[]
    edges=collections.Counter()
    signed_volume=0
    for i in range(triangles):
        values=struct.unpack_from('<12fH',data,84+50*i)
        vertices=[tuple(values[3+3*j:6+3*j]) for j in range(3)]
        assert all(math.isfinite(x) for v in vertices for x in v),path
        points.extend(vertices)
        keys=[tuple(round(x,5) for x in v) for v in vertices]
        for j in range(3):edges[tuple(sorted((keys[j],keys[(j+1)%3])))]+=1
        a,b,c=vertices
        signed_volume+=(a[0]*(b[1]*c[2]-b[2]*c[1])+a[1]*(b[2]*c[0]-b[0]*c[2])+a[2]*(b[0]*c[1]-b[1]*c[0]))/6
    minima=[min(v[i] for v in points) for i in range(3)]
    maxima=[max(v[i] for v in points) for i in range(3)]
    unpaired=sum(n!=2 for n in edges.values())
    assert unpaired==0,(path,unpaired)
    assert min(minima)>=-.001,(path,minima)
    assert abs(minima[2])<.001,(path,minima)
    assert signed_volume>0,(path,signed_volume)
    results.append({'file':path.name,'triangles':triangles,'dimensions_mm':[round(b-a,3) for a,b in zip(minima,maxima)],'watertight':True,'volume_mm3':round(signed_volume,3)})
assert len(results)==14
for path in directory.glob('*.f3d'):
    with zipfile.ZipFile(path) as archive:assert archive.testzip() is None,path
(directory/'verification-stl.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(json.dumps(results))
