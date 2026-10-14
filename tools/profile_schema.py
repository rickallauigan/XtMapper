import json,re
from pathlib import Path
text=Path('app/src/main/java/xtr/keymapper/structured/ProfileDocument.kt').read_text()
layouts={a:fields.split() for a,tag,fields in re.findall(r'"(\w+)" to layout\("(\w+)","([^"]+)"\)',text)}
layouts['fixed_touch']=['input','position.x','position.y','offset','repeat_ms']
variants=[]
for action,fields in layouts.items():
 props={'action':{'const':action},'input':{'type':'string','minLength':1,'maxLength':200},'name':{'type':'string','maxLength':80},'description':{'type':'string','maxLength':1000},'isEnabled':{'type':'boolean','default':True}}
 root={'type':'object','additionalProperties':False,'properties':props,'required':['action','input']}
 for path in fields:
  if path=='input':continue
  parts=path.split('.');obj=root
  for key in parts[:-1]:
   obj=obj['properties'].setdefault(key,{'type':'object','additionalProperties':False,'properties':{},'required':[]})
  key=parts[-1]
  typ='boolean' if path in ['aim.invert_y','auto_active','limited_bounds','nonlinear','toggle'] else 'string' if path in ['modifier','end_input','macro_id'] or path.startswith('directions.') else 'integer' if path=='repeat_ms' or action=='movement_pad' and path.startswith('size.') else 'number'
  spec={'type':typ}
  if path=='repeat_ms':spec.update(minimum=60,maximum=60000)
  if path in ['radius','aim.radius','sensitivity.x','sensitivity.y','aim.sensitivity.x','aim.sensitivity.y','size.width','size.height']:spec.update(exclusiveMinimum=0)
  if path in ['dead_zone','aim.dead_zone']:spec.update(minimum=0,exclusiveMaximum=1)
  obj['properties'][key]=spec
  if path not in ['offset','repeat_ms']:obj.setdefault('required',[]).append(key)
 for key in root['properties']:
  if key not in ['action','input','name','description','isEnabled','offset','repeat_ms']:root['required'].append(key)
 variants.append(root)
schema={'$schema':'https://json-schema.org/draft/2020-12/schema','$id':'https://github.com/rickallauigan/XtMapper/schema/v1/profile.schema.json','title':'XtMapper structured profile v1','type':'object','additionalProperties':False,'required':['schema_version','profile','bindings'],'properties':{
 'schema_version':{'const':1},'profile':{'type':'object','additionalProperties':False,'required':['name','game_package','screen'],'properties':{'name':{'type':'string','minLength':1,'maxLength':80},'description':{'type':'string','maxLength':1000},'game_package':{'type':'string','pattern':r'^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$'},'isEnabled':{'type':'boolean','default':True},'screen':{'type':'object','additionalProperties':False,'required':['width','height'],'properties':{x:{'type':'integer','minimum':1,'maximum':32768} for x in ['width','height']}}}},
 'bindings':{'type':'array','maxItems':200,'items':{'oneOf':variants}},'camera':{'oneOf':[v for v in variants if v['properties']['action']['const'] in ['camera','stick_camera']]}}}
# Top-level stick_camera permits implicit RIGHT_STICK.
schema['properties']['camera']['oneOf'][1 if schema['properties']['camera']['oneOf'][1]['properties']['action']['const']=='stick_camera' else 0]=json.loads(json.dumps(next(v for v in variants if v['properties']['action']['const']=='stick_camera')))
for v in schema['properties']['camera']['oneOf']:
 if v['properties']['action']['const']=='stick_camera':v['required'].remove('input')
import sys
expected=json.dumps(schema,indent=2)+'\n'
target=Path('schema/v1/profile.schema.json')
if '--check' in sys.argv:
 assert target.read_text()==expected, 'Schema differs from native action descriptors; regenerate.'
else:
 target.write_text(expected)
