// Audit-only harness; reads and exercises the isolated production build, never production.
const { chromium } = require('/private/tmp/trustkart-audit-20260930/frontend/node_modules/@playwright/test');
const { default: AxeBuilder } = require('/private/tmp/trustkart-audit-20260930/frontend/node_modules/@axe-core/playwright');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const base = 'http://127.0.0.1:15173';
const save = (name, data) => fs.writeFileSync(path.join(root,'evidence',name), JSON.stringify(data,null,2));
async function api(context, method, endpoint, data, key) {
  if(method !== 'GET') await context.request.get(base+'/api/v1/auth/csrf');
  const cookies = await context.cookies();
  const csrf = cookies.find(c=>c.name==='XSRF-TOKEN')?.value || '';
  const res = await context.request.fetch(base+'/api/v1'+endpoint,{ method, data,
    headers:{'X-XSRF-TOKEN':csrf, 'Idempotency-Key':key || crypto.randomUUID()} });
  const text = await res.text();
  if(!res.ok()) throw Error(`${method} ${endpoint}: ${res.status()} ${text}`);
  return text ? JSON.parse(text):null;
}
async function main() {
 const browser=await chromium.launch();
 const context=await browser.newContext({viewport:{width:1440,height:900}, reducedMotion:'reduce',colorScheme:'dark'});
 await api(context,'POST','/auth/register',{email:`audit-${Date.now()}@example.test`,password:'independent-audit-local-password-93',displayName:'Audit Shopper'});
 const products=(await api(context,'GET','/catalog/products?size=60')).items;
 const chosen=products.filter(p=>p.stockStatus==='IN_STOCK');
 await api(context,'PUT','/wallet/mode',{mode:'UNLIMITED'});
 await api(context,'POST','/cart/items',{productId:chosen[0].id,quantity:1});
 const quote=await api(context,'GET','/checkout/quote');
 const purchase=await api(context,'POST','/purchases',{deliveryPreset:'ADDRESS',simulationAddress:{label:'Home',fullName:'Audit Shopper',line1:'1 Audit Lane',city:'Springfield',region:'IL',postalCode:'62701',country:'US'},expectedTotal:quote.total});
 await api(context,'POST','/cart/items',{productId:chosen[1].id,quantity:1});
 await api(context,'POST','/wishlist/items',{productId:chosen[2].id});
 const page=await context.newPage();
 const errors=[];const responses=[];
 page.on('pageerror',e=>errors.push({route:page.url(),message:e.message}));
 page.on('response',r=>{if(r.url().includes('/api/'))responses.push({url:r.url().replace(base,''),status:r.status()});});
 const routes=[['home','/'],['search','/search?q=laptop'],['deals','/deals'],['category','/c/laptops'],['curated','/collections/dream-gpus'],['product','/p/apple-iphone-18-pro'],['compare','/compare?slugs=amd-ryzen-9-9950x&slugs=intel-core-ultra-9-285k'],['cart','/cart'],['checkout','/checkout'],['wallet','/wallet'],['collection','/collection'],['wishlist','/wishlist'],['rankings','/rankings'],['signin','/signin'],['signup','/signup'],['account','/account'],['orders','/account/purchases'],['tracking','/account/purchases/'+purchase.id],['notifications','/account/notifications'],['ranking-settings','/account/rankings'],['security','/account/security'],['addresses','/account/addresses'],['about','/about'],['project','/about/project'],['privacy','/about/privacy'],['credits','/about/credits'],['404','/audit-unknown-route']];
 save('fixture.json',{purchaseId:purchase.id,productSlug:chosen[1].slug,routeCount:routes.length});
 await context.storageState({path:'/private/tmp/trustkart-audit-20260930/audit-browser-state.json'});
 const matrix=fs.existsSync(path.join(root,'evidence/route-matrix.json'))?JSON.parse(fs.readFileSync(path.join(root,'evidence/route-matrix.json'))):[];
 const axe=fs.existsSync(path.join(root,'evidence/accessibility.json'))?JSON.parse(fs.readFileSync(path.join(root,'evidence/accessibility.json'))):[];
 // Desktop, tablet, then mobile; additional required widths after comprehensive route walk.
 for(const [width,height] of [[1440,900],[768,1024],[390,844],[1024,768],[1920,1080]]){
  await page.setViewportSize({width,height});
  for(const [name,url] of routes){
   if(matrix.some(r=>r.name===name&&r.width===width&&r.height===height))continue;
   await page.goto(base+url,{waitUntil:'domcontentloaded'});
   await page.waitForLoadState('networkidle',{timeout:5000}).catch(e=>errors.push({route:url,message:'networkidle timeout'}));
   const data=await page.evaluate(()=>({title:document.title,heading:document.querySelector('h1')?.textContent,overflow:document.documentElement.scrollWidth-innerWidth,dom:document.querySelectorAll('*').length,dialogs:document.querySelectorAll('dialog[open]').length,brokenImages:[...document.images].filter(i=>i.complete&&!i.naturalWidth).map(i=>i.getAttribute('src')),bodyText:document.querySelector('main')?.innerText?.slice(0,700)}));
   matrix.push({name,url,width,height,...data});
   await page.screenshot({path:path.join(root,'screenshots',`${name}-${width}.png`),fullPage:true});
   if(width===1440||width===390){
    const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze();
    axe.push({name,width,violations:result.violations.map(v=>({id:v.id,impact:v.impact,description:v.description,help:v.help,nodes:v.nodes.map(n=>({target:n.target,summary:n.failureSummary,html:n.html}))}))});
   }
   save('route-matrix.json',matrix);save('accessibility.json',axe);save('browser-errors.json',errors);save('browser-api-responses.json',responses);
   console.log(name,width,data.overflow,data.heading);
  }
 }
 for(const [width,height] of [[320,568],[360,800],[375,812],[430,932],[600,960],[820,1180],[1024,1366],[1280,800],[1728,1117]]){
  await page.setViewportSize({width,height});
  for(const name of ['home','search','product','compare','cart','checkout','rankings','tracking']){
   const [,url]=routes.find(r=>r[0]===name);
   if(matrix.some(r=>r.name===name&&r.width===width&&r.height===height))continue;
   await page.goto(base+url,{waitUntil:'domcontentloaded'});
   await page.waitForLoadState('networkidle',{timeout:5000}).catch(e=>errors.push({route:url,message:'networkidle timeout'}));
   matrix.push({name,url,width,height,...await page.evaluate(()=>({overflow:document.documentElement.scrollWidth-innerWidth,heading:document.querySelector('h1')?.textContent}))});
   if(name==='home'||width===320)await page.screenshot({path:path.join(root,'screenshots',`${name}-${width}x${height}.png`),fullPage:true});
  }
  save('route-matrix.json',matrix);console.log('additional viewport',width,height);
 }
 save('browser-errors.json',errors);save('browser-api-responses.json',responses);
 save('fixture.json',{purchaseId:purchase.id,productSlug:chosen[1].slug,routeCount:routes.length});
 // Keep cookies only in the temporary snapshot, not deliverable evidence.
 await context.storageState({path:'/private/tmp/trustkart-audit-20260930/audit-browser-state.json'});
 await browser.close();
}
main().catch(e=>{console.error(e);process.exit(1)});
