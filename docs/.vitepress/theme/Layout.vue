<script setup>
import DefaultTheme from 'vitepress/theme'
import AsideLinks from '../components/aside/AsideLinks.vue'

const { Layout } = DefaultTheme
</script>

<template>
  <Layout>
    <template #aside-outline-after>
      <AsideLinks />
    </template>
  </Layout>
</template>
