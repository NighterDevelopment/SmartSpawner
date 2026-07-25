<script setup>
import DefaultTheme from 'vitepress/theme'
import AsideLinks from '../components/aside/AsideLinks.vue'
import HeroSlideshow from '../components/home/HeroSlideshow.vue'

const { Layout } = DefaultTheme
</script>

<template>
  <Layout>
    <template #home-hero-image>
      <HeroSlideshow />
    </template>
    <template #aside-outline-after>
      <AsideLinks />
    </template>
  </Layout>
</template>
