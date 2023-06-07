/// <reference types="vitest" />
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import svgLoader from 'vite-svg-loader';
import * as path from 'path';

/**
 * Vite Configuration
 *
 * Please update any changes in `playwright-ct.config.ts` under `config.use.ctViteConfig`.
 */

// See: https://vitejs.dev/config/
// See: https://vitejs.dev/config/server-options.html#server-proxy
export default defineConfig({
	optimizeDeps: {
		exclude: ['mathlive']
	},
	// Syntax sugar for specifying imports
	resolve: {
		alias: [
			{ find: /^~/, replacement: path.resolve(__dirname, './node_modules') + '/' },
			{ find: 'path', replacement: path.resolve(__dirname, './node_modules/path-browserify/') },
			{ find: '@', replacement: path.resolve(__dirname, './src') },
			{ find: '@assets', replacement: path.resolve(__dirname, './src/assets') },
			{ find: '@node_modules', replacement: path.resolve(__dirname, '../../../node_modules') },
			{ find: '@graph-scaffolder', replacement: path.resolve(__dirname, '../graph-scaffolder/src') }
		]
	},
	base: '/app/',
	server: {
		port: 8080,
		strictPort: true,
		// Due to the reverse proxy being present the following
		// HMR port option is set as per NOTE in the docs
		// https://vitejs.dev/config/server-options.html#server-hmr
		hmr: {
			port: 8080
		}
	},
	preview: {
		port: 8080
	},
	build: {
		target: 'esnext'
	},
	plugins: [
		vue({
			template: {
				compilerOptions: {
					// treat all components starting with `facet` as custom elements
					// ignore facets as custom elements
					isCustomElement: (tag) =>
						tag.startsWith('facet-') || tag === 'math-field' || tag == 'katex'
				}
			}
		}),
		// By default SVGs are imported as URL in order to easily reference them in img tags
		// In order to import SVGs as components you must add '?component' as a suffix of an SVG's path
		svgLoader({ defaultImport: 'url' })
	],
	test: {
		include: ['tests/unit/**/*.{test,spec}.{ts,mts}']
	}
});
