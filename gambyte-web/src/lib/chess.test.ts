import { describe, expect, it } from 'vitest';
import { checkedKing, destinations, formatClock, isPromotion, replay, timeControlLabel, tryMove } from './chess';

describe('chess helpers', () => {
  it('replays UCI moves', () => {
    const c = replay('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', ['e2e4', 'e7e5', 'g1f3']);
    expect(c.history()).toEqual(['e4', 'e5', 'Nf3']);
  });

  it('lists legal destinations', () => {
    const c = replay('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', []);
    expect(destinations(c, 'g1').sort()).toEqual(['f3', 'h3']);
  });

  it('detects promotions and illegal moves', () => {
    const c = replay('8/4P3/8/8/8/k7/8/K7 w - - 0 1', []);
    expect(isPromotion(c, 'e7', 'e8')).toBe(true);
    expect(tryMove(c, 'e7', 'e6')).toBeNull();
    expect(tryMove(c, 'e7', 'e8', 'n')).toBe('e7e8n');
  });

  it('finds the king in check', () => {
    const c = replay('rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2', ['d8h4']);
    expect(checkedKing(c)).toBe('e1');
  });

  it('formats clocks like a chess clock', () => {
    expect(formatClock(180_000)).toBe('3:00');
    expect(formatClock(65_400)).toBe('1:06');
    expect(formatClock(9_450)).toBe('9.4');
    expect(formatClock(-5)).toBe('0.0');
    expect(formatClock(3_725_000)).toBe('1:02:05');
  });

  it('labels time controls', () => {
    expect(timeControlLabel(180_000, 2_000)).toBe('3+2');
    expect(timeControlLabel(0, 0)).toBe('Unlimited');
  });
});
