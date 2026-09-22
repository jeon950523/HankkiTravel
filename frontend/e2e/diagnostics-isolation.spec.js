import { test, expect } from '@playwright/test'
import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'

test('production 진단 URL은 일반 404이며 health 요청을 하지 않는다', async ({page})=>{
 const errors=[]
 const health=[]
 page.on('pageerror',error=>errors.push(error.message))
 page.on('request',request=>{if(request.url().includes('/actuator/health'))health.push(request.url())})
 for(const url of ['/dev/diagnostics','/dev/diagnostics?dev=true','/missing-p031-page']) {
  await page.goto(url)
  await expect(page.getByRole('heading',{level:1})).toHaveText('이 페이지를찾을 수 없어요.')
  await expect(page).toHaveTitle('페이지를 찾을 수 없어요 · 한끼여행')
  await expect(page.getByRole('navigation',{name:'주요 메뉴'})).toBeVisible()
  await expect(page.getByText('P0.1 · FOUNDATION')).toHaveCount(0)
 }
 expect(health).toEqual([])
 expect(errors).toEqual([])
 await page.getByRole('link',{name:'홈으로 돌아가기'}).click()
 await expect(page).toHaveURL('/')
})
test('production 산출물에는 진단 구현과 health store가 포함되지 않는다', async ()=>{
 const root=path.resolve('dist')
 const files=await readdir(root,{recursive:true})
 const markers=['/dev/diagnostics','P0.1 · FOUNDATION','서비스 연결을 확인하고 있어요.','Health request failed','/actuator/health','FoundationView']
 for(const file of files.filter(file=>/\.(js|css|html|json|webmanifest)$/.test(file))) {
  const body=await readFile(path.join(root,file),'utf8')
  for(const marker of markers) expect(body, file+' excludes '+marker).not.toContain(marker)
 }
})
