<script setup>
import { computed } from 'vue'
import {
  Archive, ArrowDownAZ, ArrowRight, ArrowUpRight, Blocks, BookOpen, Box,
  Bug, Check, ChevronDown, CircleDollarSign, Code2, Download, FileCode2,
  Gamepad2, GitFork, Globe2, Heart, Languages, Layers3, LayoutDashboard, List,
  ListFilter, MapPinned, MessageCircle, Package, Palette, Pencil, Plug,
  Rocket, Server, Settings, Settings2, ShieldCheck, ShoppingCart, Sparkles,
  Star, Store, Swords, Tag, Terminal, Wrench, Zap
} from '@lucide/vue'

const ICONS = {
  Archive, ArrowDownAZ, ArrowRight, ArrowUpRight, Blocks, BookOpen, Box,
  Bug, Check, ChevronDown, CircleDollarSign, Code2, Download, FileCode2,
  Gamepad2, Github: GitFork, Globe2, Heart, Languages, Layers3, LayoutDashboard, List,
  ListFilter, MapPinned, MessageCircle, Package, Palette, Pencil, Plug,
  Rocket, Server, Settings, Settings2, ShieldCheck, ShoppingCart, Sparkles,
  Star, Store, Swords, Tag, Terminal, Wrench, Zap
}

const props = defineProps({
  name: String,
  size: { type: Number, default: 22 }
})

const icon = computed(() => props.name ? (ICONS[props.name] ?? null) : null)
</script>

<template>
  <component :is="icon" v-if="icon" :size="size" :stroke-width="1.75" aria-hidden="true" />
</template>
