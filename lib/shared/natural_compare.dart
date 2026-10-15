int compareNatural(String a, String b) {
  final left = a.toLowerCase();
  final right = b.toLowerCase();
  var leftIndex = 0;
  var rightIndex = 0;
  while (leftIndex < left.length && rightIndex < right.length) {
    final leftIsDigit = _isDigit(left.codeUnitAt(leftIndex));
    final rightIsDigit = _isDigit(right.codeUnitAt(rightIndex));
    if (leftIsDigit && rightIsDigit) {
      final leftStart = leftIndex;
      while (leftIndex < left.length && _isDigit(left.codeUnitAt(leftIndex))) {
        leftIndex++;
      }
      final rightStart = rightIndex;
      while (rightIndex < right.length && _isDigit(right.codeUnitAt(rightIndex))) {
        rightIndex++;
      }
      final numberComparison = BigInt.parse(left.substring(leftStart, leftIndex))
          .compareTo(BigInt.parse(right.substring(rightStart, rightIndex)));
      if (numberComparison != 0) return numberComparison;
      continue;
    }
    final characterComparison = left.codeUnitAt(leftIndex).compareTo(right.codeUnitAt(rightIndex));
    if (characterComparison != 0) return characterComparison;
    leftIndex++;
    rightIndex++;
  }
  return (left.length - leftIndex).compareTo(right.length - rightIndex);
}

bool _isDigit(int codeUnit) => codeUnit >= 0x30 && codeUnit <= 0x39;
