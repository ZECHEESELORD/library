package sh.harold.creative.library.menu;

import java.util.Objects;

public sealed interface ReactiveGeometryAction permits ReactiveGeometryAction.JumpToFirstTabs,
        ReactiveGeometryAction.JumpToLastTabs, ReactiveGeometryAction.NextPage, ReactiveGeometryAction.NextTabs,
        ReactiveGeometryAction.PreviousPage, ReactiveGeometryAction.PreviousTabs, ReactiveGeometryAction.SwitchTab {

    record PreviousPage() implements ReactiveGeometryAction {
    }

    record NextPage() implements ReactiveGeometryAction {
    }

    record SwitchTab(String tabId) implements ReactiveGeometryAction {

        public SwitchTab {
            Objects.requireNonNull(tabId, "tabId");
            if (tabId.isBlank()) {
                throw new IllegalArgumentException("tabId cannot be blank");
            }
        }
    }

    record PreviousTabs() implements ReactiveGeometryAction {
    }

    record NextTabs() implements ReactiveGeometryAction {
    }

    record JumpToFirstTabs() implements ReactiveGeometryAction {
    }

    record JumpToLastTabs() implements ReactiveGeometryAction {
    }
}
