import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { formatDate, money } from '../../shared/format';

export interface DailyValue {
  readonly date: string; // yyyy-MM-dd
  readonly value: number;
}

const WIDTH = 720;
const HEIGHT = 220;
const PAD = { top: 12, right: 8, bottom: 28, left: 56 };

/**
 * Colunas da receita líquida por dia (uma série: o título nomeia, sem legenda). Colunas finas (até 24 px) com
 * ponta arredondada e base reta, grade em linha fina, dica ao passar o mouse ou focar e tabela equivalente.
 */
@Component({
  selector: 'app-revenue-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chart">
      <svg
        [attr.viewBox]="'0 0 ' + width + ' ' + height"
        role="img"
        [attr.aria-label]="ariaLabel()"
        preserveAspectRatio="none"
      >
        @for (tick of ticks(); track tick.value) {
          <line
            class="grid"
            [attr.x1]="pad.left"
            [attr.x2]="width - pad.right"
            [attr.y1]="tick.y"
            [attr.y2]="tick.y"
          />
          <text class="axis" [attr.x]="pad.left - 6" [attr.y]="tick.y + 4" text-anchor="end">
            {{ tick.label }}
          </text>
        }
        @for (bar of bars(); track bar.date) {
          <g
            class="slot"
            tabindex="0"
            [attr.aria-label]="bar.title"
            (mouseenter)="active.set(bar)"
            (mouseleave)="active.set(null)"
            (focus)="active.set(bar)"
            (blur)="active.set(null)"
          >
            <rect
              class="hit"
              [attr.x]="bar.slotX"
              [attr.y]="pad.top"
              [attr.width]="bar.slotWidth"
              [attr.height]="plotHeight"
            />
            @if (bar.height > 0) {
              <path class="bar" [attr.d]="bar.path" />
            }
          </g>
        }
        @for (label of xLabels(); track label.date) {
          <text class="axis" [attr.x]="label.x" [attr.y]="height - 8" text-anchor="middle">
            {{ label.text }}
          </text>
        }
      </svg>
      @if (active(); as bar) {
        <div class="tooltip" [style.left.%]="(bar.center / width) * 100" role="status">
          <strong>{{ bar.value }}</strong>
          <span>{{ bar.day }}</span>
        </div>
      }
    </div>
    <details>
      <summary>Ver como tabela</summary>
      <div class="table-wrap">
        <table>
          <caption class="visually-hidden">
            {{
              ariaLabel()
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">Dia</th>
              <th scope="col" class="num">Receita líquida</th>
            </tr>
          </thead>
          <tbody>
            @for (row of series(); track row.date) {
              <tr>
                <td>{{ format(row.date) }}</td>
                <td class="num">{{ money(row.value) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </details>
  `,
  styles: `
    .chart {
      position: relative;
    }
    svg {
      width: 100%;
      height: 14rem;
      display: block;
      overflow: visible;
    }
    .grid {
      stroke: var(--color-border);
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
    }
    .axis {
      fill: var(--color-muted);
      font-size: 11px;
      font-variant-numeric: tabular-nums;
    }
    .hit {
      fill: transparent;
    }
    .bar {
      fill: var(--color-primary);
    }
    .slot:hover .hit,
    .slot:focus .hit {
      fill: rgb(29 79 215 / 0.06);
    }
    .slot:focus {
      outline: none;
    }
    .tooltip {
      position: absolute;
      top: 0;
      transform: translateX(-50%);
      display: grid;
      padding: var(--space-1) var(--space-2);
      background: var(--color-night);
      color: #fff;
      border-radius: var(--radius-sm);
      font-size: 0.8125rem;
      white-space: nowrap;
      pointer-events: none;
    }
    details {
      margin-top: var(--space-2);
    }
    summary {
      cursor: pointer;
      color: var(--color-primary);
      font-weight: 600;
    }
    .table-wrap {
      max-height: 16rem;
      margin-top: var(--space-2);
    }
  `,
})
export class RevenueChart {
  readonly series = input.required<readonly DailyValue[]>();
  readonly currency = input('BRL');

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly pad = PAD;
  protected readonly plotHeight = HEIGHT - PAD.top - PAD.bottom;
  protected readonly active = signal<{ center: number; value: string; day: string } | null>(null);
  protected readonly format = formatDate;

  protected money(value: number): string {
    return money(value, this.currency());
  }

  private readonly max = computed(() => niceMax(Math.max(0, ...this.series().map((d) => d.value))));

  protected readonly ticks = computed(() => {
    const max = this.max();
    return [0, 0.25, 0.5, 0.75, 1].map((f) => ({
      value: f * max,
      y: PAD.top + this.plotHeight * (1 - f),
      label: compact(f * max),
    }));
  });

  protected readonly bars = computed(() => {
    const data = this.series();
    const plotWidth = WIDTH - PAD.left - PAD.right;
    const slotWidth = plotWidth / Math.max(data.length, 1);
    const barWidth = Math.max(2, Math.min(24, slotWidth - 2));
    const baseline = PAD.top + this.plotHeight;
    return data.map((d, i) => {
      const slotX = PAD.left + i * slotWidth;
      const center = slotX + slotWidth / 2;
      const height = this.max() === 0 ? 0 : (Math.max(d.value, 0) / this.max()) * this.plotHeight;
      const x = center - barWidth / 2;
      const r = Math.min(4, barWidth / 2, height);
      const top = baseline - height;
      // Ponta arredondada (4 px), base reta.
      const path =
        `M${x},${baseline} V${top + r} Q${x},${top} ${x + r},${top} ` +
        `H${x + barWidth - r} Q${x + barWidth},${top} ${x + barWidth},${top + r} V${baseline} Z`;
      const value = money(d.value, this.currency());
      const day = formatDate(d.date);
      return {
        date: d.date,
        slotX,
        slotWidth,
        center,
        height,
        path,
        value,
        day,
        title: `${day}: ${value}`,
      };
    });
  });

  /** No máximo ~8 rótulos no eixo x, sempre incluindo o primeiro e o último dia. */
  protected readonly xLabels = computed(() => {
    const bars = this.bars();
    const step = Math.max(1, Math.ceil(bars.length / 8));
    return bars
      .filter((_, i) => i % step === 0 || i === bars.length - 1)
      .map((b) => ({ date: b.date, x: b.center, text: b.day.slice(0, 5) }));
  });

  protected readonly ariaLabel = computed(() => {
    const total = this.series().reduce((sum, d) => sum + d.value, 0);
    return `Receita líquida por dia, ${this.series().length} dias, total ${money(total, this.currency())}`;
  });
}

/** Topo do eixo em número redondo (1, 2, 2,5 ou 5 vezes uma potência de 10). */
export function niceMax(value: number): number {
  if (value <= 0) return 1;
  const power = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 2.5, 5, 10].find((m) => m * power >= value) ?? 10;
  return step * power;
}

export function compact(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    notation: value >= 10_000 ? 'compact' : 'standard',
    maximumFractionDigits: 1,
  }).format(value);
}
