import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

const IMG_W = 3600;
const IMG_H = 1620;

@Component({
  selector: 'app-glass-shell',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  templateUrl: './glass-shell.html',
  styleUrl: './glass-shell.scss',
})
export class GlassShell implements OnChanges, OnInit {

  @Input() backgroundUrl = '/world-map.png';

  @Input() align: 'left' | 'right' | 'center' = 'right';

  @Input() showBrand = true;

  @Input() focalX = 71.9;

  @Input() focalY = 42.0;

  @Input() zoom = 1.35;

  bgWidthPx = 0;

  bgHeightPx = 0;

  bgBaseLeftPx = 0;

  bgBaseTopPx = 0;

  bgTransform = 'translate(0px, 0px)';

  private reducedMotion =
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  ngOnInit(): void {
    this.recompute();
  }

  ngOnChanges(): void {
    this.recompute();
  }

  onResize(): void {
    this.recompute();
  }

  private recompute(): void {
    if (typeof window === 'undefined') return;

    const Cw = window.innerWidth;
    const Ch = window.innerHeight;

    /*
     * Calculate the scale required to cover the viewport.
     * The zoom input is then applied on top of that scale.
     */
    const coverScale = Math.max(
      Cw / IMG_W,
      Ch / IMG_H
    );

    const S = coverScale * this.zoom;

    const Rw = IMG_W * S;
    const Rh = IMG_H * S;

    this.bgWidthPx = Rw;
    this.bgHeightPx = Rh;

    /*
     * Start the enlarged image centered inside the viewport.
     */
    this.bgBaseLeftPx = (Cw - Rw) / 2;
    this.bgBaseTopPx = (Ch - Rh) / 2;

    /*
     * Focal-point positioning.
     */
    const fx = this.focalX / 100;
    const fy = this.focalY / 100;

    let tx = -(Rw * (fx - 0.5));
    let ty = -(Rh * (fy - 0.5));

    /*
     * Prevent the image from being moved so far that
     * empty space appears around the viewport.
     */
    const maxTx = Math.max(0, (Rw - Cw) / 2);
    const maxTy = Math.max(0, (Rh - Ch) / 2);

    tx = Math.max(
      -maxTx,
      Math.min(maxTx, tx)
    );

    ty = Math.max(
      -maxTy,
      Math.min(maxTy, ty)
    );

    this.bgTransform = `translate(${tx}px, ${ty}px)`;
  }
}