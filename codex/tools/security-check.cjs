// Safe local-only negative/ownership and session-revocation checks. No token values are retained in evidence.
const {request}=require('/private/tmp/trustkart-audit-20260930/frontend/node_modules/@playwright/test');
const {execFileSync}=require('node:child_process');const fs=require('node:fs');const path=require('node:path');
const base='http://127.0.0.1:15173/api/v1';const out=[];
async function call(c,method,p,data,extra={}){
 if(method!=='GET')await c.get(base+'/auth/csrf');
 const state=await c.storageState();const token=state.cookies.find(x=>x.name==='XSRF-TOKEN')?.value||'';
 return c.fetch(base+p,{method,data,headers:{'X-XSRF-TOKEN':token,'Idempotency-Key':crypto.randomUUID(),...extra}});
}
async function check(name,res,expected){const actual=res.status();out.push({name,expected,actual,pass:actual===expected,body:actual>=400?(await res.text()).slice(0,350):undefined});}
(async()=>{
 const a=await request.newContext();const b=await request.newContext();
 await check('Guest cannot read sessions',await call(b,'GET','/me/sessions'),401);
 await check('Guest cannot read admin rankings',await call(b,'GET','/admin/leaderboards'),401);
 await check('Write without CSRF',await b.post(base+'/cart/items',{data:{productId:1,quantity:1}}),403);
 await check('Register audit account',await call(a,'POST','/auth/register',{email:`security-audit-${Date.now()}@example.test`,password:'independent-local-security-check-94',displayName:'Security Audit'}),201);
 await check('Customer cannot read admin rankings',await call(a,'GET','/admin/leaderboards'),403);
 const products=await (await call(a,'GET','/catalog/products?size=60')).json();const product=products.items.find(x=>x.stockStatus==='IN_STOCK'&&Number(x.price)<100000);
 await check('Negative quantity rejected',await call(a,'POST','/cart/items',{productId:product.id,quantity:-1}),400);
 await check('Client price rejected',await call(a,'POST','/cart/items',{productId:product.id,quantity:1,price:'0.01'}),400);
 await call(a,'POST','/cart/items',{productId:product.id,quantity:1});
 const cart=await (await call(a,'GET','/cart')).json();
 await check('Other shopper cannot edit cart line',await call(b,'PATCH','/cart/items/'+cart.items[0].id,{quantity:2}),404);
 const quote=await(await call(a,'GET','/checkout/quote')).json();
 const data={deliveryPreset:'ADDRESS',simulationAddress:{label:'Home',fullName:'Security Audit',line1:'1 Local Lane',city:'Springfield',region:'IL',postalCode:'62701',country:'US'},expectedTotal:quote.total};
 await check('Forged checkout total rejected',await call(a,'POST','/purchases',{...data,expectedTotal:'0.01'}),409);
 const key=crypto.randomUUID();const r1=await call(a,'POST','/purchases',data,{'Idempotency-Key':key});const order=await r1.json();
 const r2=await call(a,'POST','/purchases',data,{'Idempotency-Key':key});const repeat=await r2.json();
 out.push({name:'Order idempotency',expected:'same order id',actual:order.id===repeat.id,pass:order.id===repeat.id});
 await check('Other shopper cannot read order',await call(b,'GET','/purchases/'+order.id),404);
 await check('Other shopper cannot refund order',await call(b,'POST','/purchases/'+order.id+'/refund'),404);
 const notes=await(await call(a,'GET','/notifications?size=30')).json();
 if(notes.items.length)await check('Other shopper cannot mark notification read',await call(b,'POST','/notifications/'+notes.items[0].id+'/read'),404);
 const stored=await a.storageState();
 const access=stored.cookies.find(c=>c.name==='tk_at');
 if(!access)throw Error('Access cookie names: '+stored.cookies.map(c=>c.name).join(','));
 const claims=JSON.parse(Buffer.from(access.value.split('.')[1],'base64url'));
 await check('Logout audit account',await call(a,'POST','/auth/logout'),204);
 const replay=await request.newContext({storageState:stored});
 await check('Old access cookie rejected after logout',await replay.get(base+'/me/sessions'),401);
 const redisKey='tk:revoked-sid:'+claims.sid;
 if(!/^tk:revoked-sid:[a-f0-9-]{36}$/.test(redisKey))throw Error('Unexpected sid format');
 const deleted=execFileSync('docker',['exec','codex-trustkart-audit-redis-1','sh','-c','redis-cli --no-auth-warning -a "$REDIS_PASSWORD" DEL "$1"','sh',redisKey],{encoding:'utf8'}).trim();
 out.push({name:'Simulate eviction of only this audit session denylist key',removedKeys:Number(deleted)});
 await check('Old revoked access cookie must remain rejected after Redis key loss',await replay.get(base+'/me/sessions'),401);
 fs.writeFileSync(path.resolve(__dirname,'../evidence/security-checks.json'),JSON.stringify(out,null,2));console.log(JSON.stringify(out,null,2));
 await a.dispose();await b.dispose();await replay.dispose();
})().catch(e=>{console.error(e);fs.writeFileSync(path.resolve(__dirname,'../evidence/security-checks.json'),JSON.stringify(out,null,2));process.exit(1)});
