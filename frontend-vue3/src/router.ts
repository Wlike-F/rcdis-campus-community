import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/hmdp/app', redirect: '/' },
    { path: '/hmdp/app/', redirect: '/' },
    { path: '/', component: () => import('./views/HomeView.vue') },
    { path: '/nearby', component: () => import('./views/NearbyView.vue') },
    { path: '/city/:id', component: () => import('./views/CityPlaceView.vue') },
    { path: '/assistant', component: () => import('./views/AiView.vue') },
    { path: '/shop/:id', component: () => import('./views/ShopView.vue') },
    { path: '/blog/:id', component: () => import('./views/BlogView.vue') },
    { path: '/publish', component: () => import('./views/PublishView.vue') },
    { path: '/profile', component: () => import('./views/ProfileView.vue') },
    { path: '/rewards', component: () => import('./views/RewardsView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
