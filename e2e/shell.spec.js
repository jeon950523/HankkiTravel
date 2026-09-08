import { test, expect, chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
const evidence = path.resolve('../docs/implementation/browser/p03')
const pages = [
 ['home', '/', '좋은 한 끼가,'],
 ['login', '/login', '우리 가족의 여행,'],
 ['profiles', '/profiles', '우리 가족을 담는'],
 ['travel', '/travel/new', '어디로, 얼마 동안'],
]
async function audit(page) {
 return page.evaluate(() => {
  const visible = el => el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0 && getComputedStyle(el).visibility !== 'hidden'
  const rgb = text => (text.match(/[\d.]+/g) || []).map(Number)
  const luminance = color => color.slice(0,3).map(v => v/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4).reduce((sum,v,i)=>sum+v*[.2126,.7152,.0722][i],0)
  const background = el => {
   while(el) { const color=rgb(getComputedStyle(el).backgroundColor); if(color.length===3 || color[3]===1) return color; el=el.parentElement }
   return [247,243,235]
  }
  const contrastFailures = []
  let minContrast = 100
  for(const el of document.querySelectorAll('body *')) {
   if(!visible(el) || ![...el.childNodes].some(n=>n.nodeType===Node.TEXT_NODE && n.textContent.trim()) || el.closest('svg')) continue
   const style=getComputedStyle(el)
   if(el.closest(':disabled')) continue
   const fg=luminance(rgb(style.color)), bg=luminance(background(el))
   const ratio=(Math.max(fg,bg)+.05)/(Math.min(fg,bg)+.05)
   const large=parseFloat(style.fontSize)>=24 || (parseFloat(style.fontSize)>=18.66 && parseInt(style.fontWeight)>=700)
   minContrast=Math.min(minContrast,ratio)
   if(ratio < (large ? 3 : 4.5)) contrastFailures.push({text:el.textContent.trim().slice(0,35),ratio})
  }
  const smallTargets = [...document.querySelectorAll('a,button,input')].filter(visible).filter(el=>!el.matches(':disabled') && !el.classList.contains('skip-link')).flatMap(el=>{
   const target=el.closest('label')||el; const rect=target.getBoundingClientRect()
   return rect.width < 43.5 || rect.height < 43.5 ? [target.textContent.trim().slice(0,40)] : []
  })
  const clipped = [...document.querySelectorAll('main *')].filter(visible).filter(el=>!el.closest('svg') && getComputedStyle(el).display!=='inline' && el.clientWidth>0 && el.scrollWidth>el.clientWidth+1).map(el=>el.tagName)
  return {overflow:document.documentElement.scrollWidth>innerWidth, contrastFailures, minContrast, smallTargets, clipped}
 })
}
for (const width of [360,390,768,1280]) {
 test('핵심 화면 UX 감사 '+width+'px', async ({page})=>{
  await mkdir(evidence,{recursive:true})
  await page.setViewportSize({width,height:900})
  const errors=[]
  page.on('pageerror', error=>errors.push(error.message))
  page.on('console', message=>{if(message.type()==='error') errors.push(message.text())})
  const results=[]
  for(const [name,url,heading] of pages) {
   await page.goto(url)
   await expect(page.locator('h1')).toContainText(heading)
   await page.evaluate(()=>document.fonts.ready)
   expect(await page.evaluate(()=>document.fonts.check('16px "Pretendard Variable"'))).toBe(true)
   const result=await audit(page);results.push({page:name,...result})
   expect(result.overflow).toBe(false)
   expect(result.clipped).toEqual([])
   expect(result.smallTargets).toEqual([])
   expect(result.contrastFailures).toEqual([])
   await page.screenshot({path:path.join(evidence,name+'-'+width+'.png'),fullPage:false})
  }
  expect(errors).toEqual([])
  await writeFile(path.join(evidence,'audit-'+width+'.json'),JSON.stringify({width,errors,results},null,2))
 })
}
test('홈 CTA, 메뉴, 프로필 편집 안내와 404 이동', async ({page})=>{
 await page.setViewportSize({width:390,height:844})
 await page.goto('/')
 await page.getByRole('link',{name:'한 끼부터 찾기',exact:true}).click()
 await expect(page).toHaveURL(/travel\/new\?start=meal$/)
 await expect(page.getByText('먹고 싶은 한 끼에서 시작해요')).toBeVisible()
 await page.getByRole('link',{name:'홈',exact:true}).click()
 await page.getByRole('link',{name:'장소부터 찾기',exact:true}).click()
 await expect(page.getByText('가보고 싶은 곳에서 시작해요')).toBeVisible()
 await page.getByRole('link',{name:'가족 프로필',exact:true}).click()
 await page.getByRole('link',{name:'새 프로필 만들기',exact:true}).click()
 await expect(page).toHaveURL(/profiles\/new$/)
 await expect(page.getByRole('button',{name:'프로필 저장 · 준비 중'})).toBeDisabled()
 expect(await page.locator('input,textarea,select').count()).toBe(0)
 await page.screenshot({path:path.join(evidence,'profile-editor-390.png'),fullPage:false})
 await page.goto('/missing-page')
 await expect(page.locator('h1')).toContainText('이 페이지를')
 await page.getByRole('link',{name:'홈으로 돌아가기'}).click()
 await expect(page).toHaveURL('/')
})
test('로그인 준비 상태는 인증 요청이나 거짓 성공을 만들지 않는다', async ({page})=>{
 await page.setViewportSize({width:390,height:844})
 const mutations=[]
 page.on('request', request=>{if(request.method()!=='GET' || /oauth2|\/api\//.test(request.url()))mutations.push(request.url())})
 await page.goto('/login')
 await expect(page.getByRole('button',{name:/카카오로 계속하기/})).toBeDisabled()
 await expect(page.getByRole('button',{name:/네이버로 계속하기/})).toBeDisabled()
 await expect(page.getByText('Google')).toHaveCount(0)
 await page.getByRole('link',{name:'로그인 없이 둘러보기'}).click()
 await expect(page).toHaveURL('/')
 await page.goto('/auth/callback?code=synthetic-code')
 await expect(page.locator('h1')).toContainText('로그인 연결을')
 await expect(page.getByText('synthetic-code')).toHaveCount(0)
 await page.screenshot({path:path.join(evidence,'callback-390.png'),fullPage:false})
 expect(mutations).toEqual([])
})
test('여행 선택은 로컬 상태이며 키보드 조작과 초기화를 지원한다', async ({page})=>{
 await page.setViewportSize({width:390,height:844})
 await page.goto('/travel/new')
 const requests=[]
 page.on('request', request=>{if(request.method()!=='GET')requests.push(request.method())})
 await page.getByRole('radio',{name:/제주/}).check()
 await page.getByRole('radio',{name:'당일',exact:true}).check()
 await expect(page.locator('.selection-summary')).toHaveText('제주 · 당일')
 await page.getByRole('radio',{name:/제주/}).focus()
 await page.keyboard.press('ArrowRight')
 await expect(page.getByRole('radio',{name:/경주/})).toBeChecked()
 await page.getByRole('radio',{name:'1박 2일',exact:true}).check()
 await expect(page.locator('.selection-summary')).toHaveText('경주 · 1박 2일')
 expect((await audit(page)).contrastFailures).toEqual([])
 await page.locator('.travel-preview').scrollIntoViewIfNeeded()
 await page.screenshot({path:path.join(evidence,'travel-selected-390.png'),fullPage:false})
 await expect(page.getByRole('button',{name:'다음으로 · 준비 중'})).toBeDisabled()
 await page.reload()
 await expect(page.getByRole('radio',{checked:true})).toHaveCount(0)
 expect(requests).toEqual([])
 expect(await page.evaluate(()=>Object.keys(localStorage))).toEqual([])
})
test('제품 화면은 health 장애와 독립적이며 키보드 초점이 이동한다', async ({page})=>{
 let healthRequests=0
 await page.route('**/actuator/health', route=>{healthRequests++;return route.abort()})
 await page.goto('/')
 await page.keyboard.press('Tab')
 await expect(page.getByRole('link',{name:'본문으로 바로가기'})).toBeFocused()
 await page.keyboard.press('Enter')
 await expect(page.locator('main')).toBeFocused()
 await page.getByRole('link',{name:'로그인',exact:true}).click()
 await expect(page.locator('h1')).toBeFocused()
 await expect(page).toHaveTitle('로그인 · 한끼여행')
 await page.getByRole('link',{name:'로그인 없이 둘러보기'}).click()
 await expect(page.locator('h1')).toBeFocused()
 expect(healthRequests).toBe(0)
})
test('PWA 설치 manifest, service worker, 오프라인 전체 Shell', async ({}, testInfo)=>{
 const context=await chromium.launchPersistentContext(testInfo.outputPath('pwa-profile'),{channel:'msedge',headless:true,viewport:{width:390,height:844}})
 const page=await context.newPage()
 try {
 await page.goto('/')
 await page.evaluate(async()=>{await navigator.serviceWorker.ready})
 await page.reload()
 await page.waitForFunction(()=>Boolean(navigator.serviceWorker.controller))
 const manifest=await (await page.request.get('/manifest.webmanifest')).json()
 expect(manifest.name).toBe('한끼여행')
 expect(manifest.display).toBe('standalone')
 expect(manifest.start_url).toBe('/')
 for(const size of [192,512]) {
  const icon=manifest.icons.find(icon=>icon.sizes===size+'x'+size)
  expect(icon).toBeTruthy()
  expect((await page.request.get(icon.src)).ok()).toBe(true)
 }
 const cdp=await context.newCDPSession(page)
 const installability=await cdp.send('Page.getInstallabilityErrors')
 expect(installability.installabilityErrors).toEqual([])
 await writeFile(path.join(evidence,'pwa-installability.json'),JSON.stringify(installability,null,2))
 await context.setOffline(true)
 for(const url of ['/','/login','/profiles','/profiles/new','/travel/new','/auth/callback','/offline-unknown']) {
  await page.goto(url)
  await expect(page.locator('h1')).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 }
 const cachedUrls=await page.evaluate(async()=>{const urls=[];for(const key of await caches.keys()){for(const request of await (await caches.open(key)).keys())urls.push(new URL(request.url).pathname)}return urls})
 expect(cachedUrls.some(url=>url.includes('/fonts/'))).toBe(true)
 expect(cachedUrls.filter(url=>/^\/(api|actuator)/.test(url))).toEqual([])
 await context.setOffline(false)
 } finally { await context.close() }
})
