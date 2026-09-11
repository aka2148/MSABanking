import { Component, ElementRef, OnDestroy, AfterViewInit, ViewChild, HostListener } from '@angular/core';
import * as THREE from 'three';

interface TransactionArc {
  curve: THREE.QuadraticBezierCurve3;
  packet: THREE.Mesh;
  t: number;
  speed: number;
  delay: number;
}

// A handful of illustrative city markers. Placement uses standard lat/long
// -> sphere-UV conversion, which won't perfectly match this specific
// stylized (AI-generated) texture's exact coastlines — treat these as
// decorative "transaction points," not surveyed locations.
const CITIES = [
  { name: 'Mumbai', lat: 19, lon: 73 },
  { name: 'London', lat: 51, lon: 0 },
  { name: 'New York', lat: 41, lon: -74 },
  { name: 'Singapore', lat: 1, lon: 104 },
  { name: 'Dubai', lat: 25, lon: 55 },
];

@Component({
  selector: 'app-globe-hero',
  standalone: true,
  templateUrl: './globe-hero.html',
  styleUrl: './globe-hero.scss',
})
export class GlobeHero implements AfterViewInit, OnDestroy {
  @ViewChild('canvasHost', { static: true }) canvasHost!: ElementRef<HTMLDivElement>;

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private globeGroup!: THREE.Group;
  private frameId = 0;
  private arcs: TransactionArc[] = [];
  private clock = new THREE.Clock();
  private isDragging = false;
  private lastPointerX = 0;
  private autoRotateSpeed = 0.09;
  private manualRotationY = 0;

  ngAfterViewInit(): void {
    this.initScene();
    this.animate();
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.frameId);
    this.renderer?.dispose();
    this.scene?.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose();
        if (Array.isArray(obj.material)) obj.material.forEach((m) => m.dispose());
        else obj.material.dispose();
      }
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    const el = this.canvasHost.nativeElement;
    this.camera.aspect = el.clientWidth / el.clientHeight;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(el.clientWidth, el.clientHeight);
  }

  onPointerDown(event: PointerEvent): void {
    this.isDragging = true;
    this.lastPointerX = event.clientX;
  }
  @HostListener('window:pointerup')
  onPointerUp(): void {
    this.isDragging = false;
  }
  @HostListener('window:pointermove', ['$event'])
  onPointerMove(event: PointerEvent): void {
    if (!this.isDragging) return;
    const delta = event.clientX - this.lastPointerX;
    this.manualRotationY += delta * 0.005;
    this.lastPointerX = event.clientX;
  }

  private latLonToVector3(lat: number, lon: number, radius: number): THREE.Vector3 {
    const phi = (90 - lat) * (Math.PI / 180);
    const theta = (lon + 180) * (Math.PI / 180);
    return new THREE.Vector3(
      -radius * Math.sin(phi) * Math.cos(theta),
      radius * Math.cos(phi),
      radius * Math.sin(phi) * Math.sin(theta)
    );
  }

  private initScene(): void {
    const el = this.canvasHost.nativeElement;
    const width = el.clientWidth;
    const height = el.clientHeight;

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
    this.camera.position.z = 6.2;

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    el.appendChild(this.renderer.domElement);

    this.globeGroup = new THREE.Group();
    this.scene.add(this.globeGroup);

    const radius = 2.2;

    // Globe sphere, textured with the map image. Unlit material — the
    // texture is already a dark/gold "glowing" style, so no extra lighting
    // is needed to sell the look.
    const texture = new THREE.TextureLoader().load('/world-map.png');
    texture.colorSpace = THREE.SRGBColorSpace;
    const globeMesh = new THREE.Mesh(
      new THREE.SphereGeometry(radius, 64, 64),
      new THREE.MeshBasicMaterial({ map: texture })
    );
    this.globeGroup.add(globeMesh);

    // Soft outer glow shell — simple additive-blended slightly-larger sphere
    const glowMesh = new THREE.Mesh(
      new THREE.SphereGeometry(radius * 1.04, 32, 32),
      new THREE.MeshBasicMaterial({
        color: 0xc79a3e,
        transparent: true,
        opacity: 0.06,
        side: THREE.BackSide,
      })
    );
    this.globeGroup.add(glowMesh);

    // City markers
    const cityPositions = CITIES.map((c) => ({
      ...c,
      pos: this.latLonToVector3(c.lat, c.lon, radius * 1.01),
    }));

    cityPositions.forEach(({ pos }) => {
      const dot = new THREE.Mesh(
        new THREE.SphereGeometry(0.02, 8, 8),
        new THREE.MeshBasicMaterial({ color: 0xffd77a })
      );
      dot.position.copy(pos);
      this.globeGroup.add(dot);
    });

    // Transaction arcs between a few city pairs
    const pairs: [number, number][] = [
      [0, 1], [1, 2], [0, 3], [3, 4], [2, 4],
    ];
    pairs.forEach(([aIdx, bIdx], i) => {
      const a = cityPositions[aIdx].pos;
      const b = cityPositions[bIdx].pos;
      const mid = a.clone().add(b).multiplyScalar(0.5).normalize().multiplyScalar(radius * 1.35);
      const curve = new THREE.QuadraticBezierCurve3(a, mid, b);

      const points = curve.getPoints(40);
      const lineGeo = new THREE.BufferGeometry().setFromPoints(points);
      const lineMat = new THREE.LineBasicMaterial({ color: 0xc79a3e, transparent: true, opacity: 0.25 });
      this.globeGroup.add(new THREE.Line(lineGeo, lineMat));

      const packet = new THREE.Mesh(
        new THREE.SphereGeometry(0.028, 8, 8),
        new THREE.MeshBasicMaterial({ color: 0xffffff })
      );
      this.globeGroup.add(packet);

      this.arcs.push({ curve, packet, t: 0, speed: 0.25 + Math.random() * 0.15, delay: i * 0.8 });
    });
  }

  private animate = (): void => {
    this.frameId = requestAnimationFrame(this.animate);
    const dt = this.clock.getDelta();
    const elapsed = this.clock.getElapsedTime();

    if (!this.isDragging) {
      this.globeGroup.rotation.y += this.autoRotateSpeed * dt;
    }
    if (this.manualRotationY !== 0) {
      this.globeGroup.rotation.y += this.manualRotationY;
      this.manualRotationY = 0;
    }

    this.arcs.forEach((arc) => {
      const active = elapsed > arc.delay;
      if (!active) return;
      arc.t += dt * arc.speed;
      if (arc.t > 1) arc.t = 0;
      const p = arc.curve.getPoint(arc.t);
      arc.packet.position.copy(p);
    });

    this.renderer.render(this.scene, this.camera);
  };
}
