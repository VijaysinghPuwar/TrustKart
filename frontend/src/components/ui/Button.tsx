import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router'
import { cn } from '@/lib/cn'

export type ButtonVariant = 'primary' | 'accent' | 'secondary' | 'ghost' | 'danger' | 'inverse'
export type ButtonSize = 'sm' | 'md' | 'lg'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-hover',
  // Orange is reserved for Add to cart (design system rule).
  accent: 'bg-accent text-on-accent hover:bg-accent-hover',
  secondary: 'border border-border-strong bg-surface text-ink hover:border-ink-subtle hover:bg-bg',
  ghost: 'text-primary hover:bg-primary-subtle',
  danger: 'bg-danger text-white hover:bg-danger-hover',
  inverse: 'bg-surface text-ink hover:bg-surface-2',
}

const sizes: Record<ButtonSize, string> = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-5 text-[15px]',
}

export function buttonClasses(variant: ButtonVariant = 'primary', size: ButtonSize = 'md', extra?: string) {
  return cn(
    'relative inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-button font-semibold',
    'transition-colors duration-100 ease-tk disabled:cursor-not-allowed disabled:bg-border disabled:text-ink-subtle',
    'disabled:border-transparent no-underline hover:no-underline',
    variants[variant],
    sizes[size],
    extra,
  )
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  /** Shows a spinner, keeps the width fixed and blocks repeat clicks. */
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    className,
    children,
    disabled,
    type = 'button',
    ...rest
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={buttonClasses(variant, size, className)}
      disabled={disabled ?? loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      <span className={cn('inline-flex items-center gap-2', loading && 'invisible')}>{children}</span>
      {loading && (
        <span className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
          <span className="size-4 animate-spin rounded-full border-2 border-current border-r-transparent" />
        </span>
      )}
    </button>
  )
})

interface ButtonLinkProps extends LinkProps {
  variant?: ButtonVariant
  size?: ButtonSize
}

export function ButtonLink({ variant = 'primary', size = 'md', className, ...rest }: ButtonLinkProps) {
  return (
    <Link
      className={buttonClasses(variant, size, typeof className === 'string' ? className : undefined)}
      {...rest}
    />
  )
}
