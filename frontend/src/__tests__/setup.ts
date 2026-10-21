import { config } from '@vue/test-utils'

// O modal vai para o <body> via Teleport; nos testes ele renderiza no proprio lugar, para o wrapper enxergar.
config.global.stubs = { ...config.global.stubs, teleport: true }
