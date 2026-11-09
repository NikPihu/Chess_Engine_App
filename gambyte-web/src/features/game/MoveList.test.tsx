import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MoveList } from './MoveList';

describe('MoveList', () => {
  it('shows moves in numbered pairs with review symbols', () => {
    render(<MoveList sans={['f3', 'e5', 'g4', 'Qh4#']} current={3} onSelect={() => {}} annotations={[undefined, undefined, '??', undefined]} />);
    expect(screen.getByText('01')).toBeInTheDocument();
    expect(screen.getByText('02')).toBeInTheDocument();
    expect(screen.getByText('??')).toBeInTheDocument();
  });

  it('jumps to a move when clicked', () => {
    const onSelect = vi.fn();
    render(<MoveList sans={['e4', 'e5', 'Nf3']} current={2} onSelect={onSelect} />);
    fireEvent.click(screen.getByText('e5'));
    expect(onSelect).toHaveBeenCalledWith(1);
    fireEvent.click(screen.getByText('Nf3')); // the latest move means "live" (null)
    expect(onSelect).toHaveBeenCalledWith(null);
  });
});
