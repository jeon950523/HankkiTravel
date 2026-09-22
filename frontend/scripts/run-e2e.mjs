import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { setTimeout as delay } from 'node:timers/promises'

const port = 5175
async function requireFreePort() {
 const probe = createServer()
 await new Promise((resolve, reject) => {
  probe.once('error', () => reject(new Error('5175 포트를 비워 주세요. 테스트는 기존 프로세스를 종료하지 않습니다.')))
  probe.listen(port, '127.0.0.1', () => probe.close(resolve))
 })
}
function start(args, env = process.env) {
 const child = spawn(process.execPath, args, { stdio: 'inherit', env, windowsHide: true })
 const done = new Promise((resolve, reject) => {
  child.once('error', reject)
  child.once('exit', (code, signal) => resolve(code ?? (signal ? 1 : 0)))
 })
 return { child, done }
}
async function ready(child) {
 for(let attempt=0; attempt<100; attempt++) {
  if(child.exitCode !== null) throw new Error('프런트엔드 테스트 서버가 종료되었습니다.')
  try { if((await fetch('http://127.0.0.1:'+port, {signal:AbortSignal.timeout(500)})).ok) return } catch {}
  await delay(200)
 }
 throw new Error('5175 테스트 서버 기동 시간 초과')
}
async function runProject(project) {
 await requireFreePort()
 const server = start(['node_modules/vite/bin/vite.js', ...(project==='development' ? [] : ['preview']), '--host', '127.0.0.1'],
  {...process.env, NODE_ENV:project==='development' ? 'development' : 'production'})
 const stop = () => server.child.kill()
 process.once('SIGINT', stop)
 process.once('SIGTERM', stop)
 try {
  await ready(server.child)
  const tests = start(['node_modules/@playwright/test/cli.js', 'test', '--project='+project])
  if(await tests.done !== 0) throw new Error(project+' 브라우저 검증 실패')
 } finally {
  stop()
  await server.done
  process.removeListener('SIGINT',stop)
  process.removeListener('SIGTERM',stop)
 }
}
try {
 await runProject('development')
 await runProject('production')
} catch(error) {
 console.error(error.message)
 process.exitCode=1
}
