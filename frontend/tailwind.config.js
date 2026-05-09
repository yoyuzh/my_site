/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          light: '#0F6BFF',
          dark: '#0066FF',
        },
        bg: {
          light: '#F4F7FB',
          dark: '#0A0A0A',
        },
        card: {
          light: '#FFFFFF',
          dark: '#111117',
        },
        accent: {
          light: '#D6E7FF',
          dark: '#0066FF',
        },
        text: {
          primary: {
            light: '#122033',
            dark: '#FFFFFF',
          },
          secondary: {
            light: '#6F7F97',
            dark: '#A1A1A1',
          },
          muted: {
            light: '#8A9BB5',
            dark: '#6F7F97',
          }
        }
      },
      fontFamily: {
        inter: ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'sans-serif'],
        funnel: ['Funnel Sans', 'sans-serif'],
        geist: ['Geist', 'sans-serif'],
      },
      boxShadow: {
        'card-light': '0 12px 30px rgba(140, 163, 196, 0.2)',
        'card-dark': '0 12px 30px rgba(0, 0, 0, 0.7)',
      },
      animation: {
        'fade-in-up': 'fadeInUp 0.5s ease-out forwards',
        'glow': 'glow 2s infinite alternate',
      },
      keyframes: {
        fadeInUp: {
          '0%': { opacity: '0', transform: 'translateY(20px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        glow: {
          '0%': { boxShadow: '0 0 5px rgba(15, 107, 255, 0.2)' },
          '100%': { boxShadow: '0 0 20px rgba(15, 107, 255, 0.6)' },
        }
      }
    },
  },
  plugins: [],
}
