import Vue from 'vue'
import VueRouter from 'vue-router'
import Login from '../views/Login.vue'
import Home from '../views/Home.vue'
import SparePartList from '../views/SparePartList.vue'

Vue.use(VueRouter)

// Fix 'Avoided redundant navigation to current location' warning
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => Object.assign({}, err, { isNavigationFailure: true }))
}
const originalReplace = VueRouter.prototype.replace
VueRouter.prototype.replace = function replace(location) {
  return originalReplace.call(this, location).catch(err => Object.assign({}, err, { isNavigationFailure: true }))
}

const router = new VueRouter({
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: Login },
    {
      path: '/home',
      component: Home,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: 'spare-parts' },
        { path: 'spare-parts', component: SparePartList, meta: { requiresAuth: true } }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !localStorage.getItem('token')) {
    next('/login')
  } else {
    next()
  }
})

export default router
