import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';

const activeCopyStates = {'queued', 'running'};

class CopyJobsNotifier extends Notifier<Map<int, CopyProgressMessage>> {
  @override
  Map<int, CopyProgressMessage> build() {
    final subscription = ref.watch(readerRepositoryProvider).copyProgress.listen((progress) {
      state = {...state, progress.jobId: progress};
    });
    ref.onDispose(subscription.cancel);
    return const {};
  }
}

final copyJobsProvider = NotifierProvider<CopyJobsNotifier, Map<int, CopyProgressMessage>>(CopyJobsNotifier.new);

final activeCopyCountProvider = Provider<int>(
  (ref) => ref.watch(copyJobsProvider).values.where((job) => activeCopyStates.contains(job.state)).length,
);
