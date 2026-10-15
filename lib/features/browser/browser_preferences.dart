import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'entry_sorting.dart';

enum BrowserLayout { grid, list }

class BrowserPreferences {
  const BrowserPreferences({
    this.sortField = SortField.name,
    this.ascending = true,
    this.layout = BrowserLayout.grid,
    this.showHidden = false,
  });

  final SortField sortField;
  final bool ascending;
  final BrowserLayout layout;
  final bool showHidden;

  BrowserPreferences copyWith({SortField? sortField, bool? ascending, BrowserLayout? layout, bool? showHidden}) =>
      BrowserPreferences(
        sortField: sortField ?? this.sortField,
        ascending: ascending ?? this.ascending,
        layout: layout ?? this.layout,
        showHidden: showHidden ?? this.showHidden,
      );
}

class BrowserPreferencesNotifier extends Notifier<BrowserPreferences> {
  @override
  BrowserPreferences build() => const BrowserPreferences();

  void setLayout(BrowserLayout layout) => state = state.copyWith(layout: layout);

  void selectSortField(SortField field) => state = field == state.sortField
      ? state.copyWith(ascending: !state.ascending)
      : state.copyWith(sortField: field, ascending: field != SortField.date);

  void toggleHidden() => state = state.copyWith(showHidden: !state.showHidden);
}

final browserPreferencesProvider = NotifierProvider<BrowserPreferencesNotifier, BrowserPreferences>(
  BrowserPreferencesNotifier.new,
);
