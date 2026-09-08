import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import ProfilesView from '../views/ProfilesView.vue'
import ProfileEditorView from '../views/ProfileEditorView.vue'
import TravelStartView from '../views/TravelStartView.vue'
import AuthCallbackView from '../views/AuthCallbackView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import FoundationView from '../views/FoundationView.vue'

export const routes = [
  { path: '/', name: 'home', component: HomeView, meta: { title: '한 끼에서 시작하는 여행' } },
  { path: '/login', name: 'login', component: LoginView, meta: { title: '로그인' } },
  { path: '/profiles', name: 'profiles', component: ProfilesView, meta: { title: '가족 프로필' } },
  { path: '/profiles/new', name: 'profile-new', component: ProfileEditorView, meta: { title: '새 가족 프로필' } },
  { path: '/travel/new', name: 'travel-new', component: TravelStartView, meta: { title: '여행 시작' } },
  { path: '/auth/callback', name: 'auth-callback', component: AuthCallbackView, meta: { title: '로그인 연결 안내' } },
  { path: '/dev/diagnostics', name: 'diagnostics', component: FoundationView, meta: { title: '개발용 연결 진단', standalone: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { title: '페이지를 찾을 수 없어요' } },
]
export const makeRouter = (history = createWebHistory()) => createRouter({
  history, routes, scrollBehavior: () => ({ top: 0 }),
})
