package sh.harold.library.movement.core.moves;

import sh.harold.library.movement.MoveDefinition;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.core.tuning.Moveset;

import java.util.ArrayList;
import java.util.List;

/** The five stock moves from 02, built from a moveset's tuning. Only enabled moves are returned. */
public final class StockMoves {

    private StockMoves() {
    }

    public static List<MoveDefinition> definitions(Moveset moveset) {
        List<MoveDefinition> definitions = new ArrayList<>();
        int buffer = moveset.core().inputBufferTicks();
        if (moveset.enables(MoveKeys.JUMP)) {
            definitions.add(new JumpMove(moveset.jump(), moveset.core().coyoteTicks()));
        }
        if (moveset.enables(MoveKeys.MANTLE)) {
            definitions.add(new MantleMove(moveset.mantle(), buffer));
        }
        if (moveset.enables(MoveKeys.ROLL)) {
            definitions.add(new RollMove(moveset.roll(), moveset.core().latencyGraceTicks()));
        }
        if (moveset.enables(MoveKeys.SLIDE)) {
            definitions.add(new SlideMove(moveset.slide(), moveset.core().chordTicks()));
        }
        if (moveset.enables(MoveKeys.WALL_JUMP)) {
            definitions.add(new WallJumpMove(moveset.wallJump()));
            definitions.add(new WallSlideMove(moveset.wallJump()));
        }
        return definitions;
    }
}
