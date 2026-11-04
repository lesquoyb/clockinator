"""Check the current L-dock STL exports, not the older inclined variants."""
import collections
import json
import math
from pathlib import Path
import struct
import zipfile


directory=Path(__file__).resolve().parents[1]/'output'
cad=json.loads((directory/'validation-dock-L.json').read_text(encoding='utf-8'))
expected={p['name']:p['volume_cm3']*1000 for p in cad['parts']}
results=[]
for filename,part in [('dock-L-body.stl','Dock body'),('dock-L-bottom-cover.stl','Bottom cover')]:
    path=directory/filename
    data=path.read_bytes()
    count=struct.unpack_from('<I',data,80)[0]
    assert len(data)==84+50*count,(filename,'invalid binary STL size')
    edges=collections.Counter()
    points=[]
    volume=0
    for i in range(count):
        values=struct.unpack_from('<12fH',data,84+50*i)
        vertices=[tuple(values[3+3*j:6+3*j]) for j in range(3)]
        assert all(math.isfinite(v) for point in vertices for v in point)
        points.extend(vertices)
        keys=[tuple(round(v,5) for v in point) for point in vertices]
        for j in range(3):edges[tuple(sorted((keys[j],keys[(j+1)%3])))]+=1
        a,b,c=vertices
        volume+=(a[0]*(b[1]*c[2]-b[2]*c[1])+a[1]*(b[2]*c[0]-b[0]*c[2])+a[2]*(b[0]*c[1]-b[1]*c[0]))/6
    assert all(n==2 for n in edges.values()),(filename,'non-watertight mesh')
    assert volume>0 and abs(volume-expected[part])<expected[part]*.001,(filename,volume,expected[part])
    results.append({'file':filename,'triangles':count,'watertight':True,'volume_mm3':round(volume,4),'cad_volume_mm3':round(expected[part],4),'bounds_mm':[[round(min(p[i] for p in points),4) for i in range(3)],[round(max(p[i] for p in points),4) for i in range(3)]]})
with zipfile.ZipFile(directory/'dock-L-assembly.f3d') as archive:assert archive.testzip() is None
(directory/'verification-stl-L.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(json.dumps(results))
