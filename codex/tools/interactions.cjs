const {chromium}=require('/private/tmp/trustkart-audit-20260930/frontend/node_modules/@playwright/test');
const fs=require('node:fs');const path=require('node:path');const root=path.resolve(__dirname,'..');const base='http://127.0.0.1:15173';
const result=[];const save=()=>fs.writeFileSync(path.join(root,'evidence/interactions.json'),JSON.stringify(result,null,2));
(async()=>{
 const browser=await chromium.launch();const c=await browser.newContext({storageState:'/private/tmp/trustkart-audit-20260930/audit-browser-state.json',viewport:{width:390,height:844},reducedMotion:'reduce',colorScheme:'dark'});const p=await c.newPage();
 const focus=()=>p.evaluate(()=>({tag:document.activeElement.tagName,text:document.activeElement.textContent?.slice(0,100),label:document.activeElement.getAttribute('aria-label'),role:document.activeElement.getAttribute('role')}));
 for(const width of [320,390,768,1440]){
  await p.setViewportSize({width,height:844});await p.goto(base+'/',{waitUntil:'networkidle'});
  await p.getByRole('button',{name:/^Notifications/}).click();
  const box=await p.getByRole('region',{name:'Notifications',exact:true}).boundingBox();
  result.push({check:'notification popover geometry',width,box,clipped:box.x<0||box.x+box.width>width});
  await p.screenshot({path:path.join(root,'screenshots',`notification-open-${width}.png`)});
  await p.keyboard.press('Escape');result.push({check:'notification Escape closes',width,open:await p.getByRole('region',{name:'Notifications',exact:true}).count()});save();
 }
 await p.setViewportSize({width:390,height:844});await p.goto(base+'/search?q=laptop',{waitUntil:'networkidle'});
 await p.getByRole('button',{name:'Filters',exact:true}).click();
 result.push({check:'filter dialog opens',box:await p.getByRole('dialog').boundingBox(),focus:await focus()});
 await p.keyboard.press('Shift+Tab');result.push({check:'filter Shift+Tab trap',focus:await focus()});
 await p.keyboard.press('Tab');await p.keyboard.press('Escape');result.push({check:'filter Escape and restored focus',open:await p.locator('dialog[open]').count(),focus:await focus()});
 await p.goto(base+'/wallet',{waitUntil:'networkidle'});await p.getByRole('button',{name:'Add funds',exact:true}).click();
 result.push({check:'wallet modal',box:await p.getByRole('dialog',{name:'Add funds',exact:true}).boundingBox()});
 await p.keyboard.press('Escape');result.push({check:'wallet Escape',open:await p.locator('dialog[open]').count(),focus:await focus()});
 await p.goto(base+'/rankings',{waitUntil:'networkidle'});await p.getByRole('tab',{name:'This Month',exact:true}).focus();
 await p.keyboard.press('ArrowRight');result.push({check:'ranking tabs ArrowRight',focus:await focus(),selected:await p.getByRole('tab',{selected:true}).innerText()});
 await p.keyboard.press('Tab');await p.keyboard.press('Space');result.push({check:'ranking tabs Tab Space',focus:await focus(),selected:await p.getByRole('tab',{selected:true}).innerText()});
 await p.goto(base+'/',{waitUntil:'networkidle'});
 const calls=[];p.on('request',r=>{if(r.url().includes('/search/suggest'))calls.push(r.url().replace(base,''))});
 const input=p.getByRole('combobox');await input.focus();await input.pressSequentially('macbook',{delay:60});await p.waitForTimeout(600);
 await input.press('ArrowDown');result.push({check:'search rapid typing 60ms/char',calls:[...calls],active:await input.getAttribute('aria-activedescendant')});
 await input.press('Escape');result.push({check:'search Escape',expanded:await input.getAttribute('aria-expanded')});
 await input.press('Enter');await p.waitForURL('**/search?q=macbook');result.push({check:'search Enter submits',url:p.url()});
 await input.fill('');await input.focus();await p.waitForTimeout(200);
 result.push({check:'recent-search remove focusability',removers:await p.getByRole('button',{name:/Remove .* from recent searches/}).evaluateAll(xs=>xs.map(e=>({name:e.getAttribute('aria-label'),tabIndex:e.tabIndex}))) });
 save();await browser.close();
})().catch(e=>{console.error(e);save();process.exit(1)});
