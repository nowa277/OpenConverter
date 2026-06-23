#!/usr/bin/env bash
# check-no-commercial.sh — fail if any commercial-element string is in the
# build output. Run after `npm run build` and before packaging.
#
# Run: bash tests/check-no-commercial.sh [dir]
#   default dir: release/ (where electron-builder outputs)
#   also checks: dist-renderer/ (Vite output)
set -e

DIRS="${1:-release dist-renderer}"

# Patterns that should NEVER appear in the shipped product.
# Sources: docs/M1-M3-original-code.md (商业元素清单)
PATTERNS=(
  # English field names
  '\bvip_type\b'
  '\buse_num\b'
  '\btrialData\b'
  '\btrial_num\b'
  '\bLoadUrl\b'
  '\bloadUrl\b'
  '\bdoLoadInfo\b'
  '\bappStoreProductConfig\b'
  '\bpermanentMemberId\b'
  '\bsubscriptionId\b'
  '\bpackage_validity\b'
  '\bol_token\b'
  '\buserInfo\b'
  '\boffline-vip\b'
  '\bwebviewUrl\b'

  # VIP / ad / license
  '\bisVip\b'
  '\bgetVipInfo\b'
  '\bdoloadUpdate\b'
  '\bdoMacMergeLogin\b'
  '\bMacMergePay\b'

  # Chinese commercial strings
  '永久'
  '试用次数'
  '升级'
  '购买'
  '会员'
  '授权'
  '许可证'

  # Remote commercial endpoints
  'jiangxiatech\.com'
  'onlinedo\.cn'
  'callmysoft\.com'
  'buy\.itunes\.apple\.com'
  'sandbox\.itunes\.apple\.com'
  'verifyReceipt'

  # Native FFI library (we don't bundle macOS dylib)
  '@inigolabs/ffi-napi'
)

found=0
for d in $DIRS; do
  if [ ! -d "$d" ]; then continue; fi
  for pat in "${PATTERNS[@]}"; do
    hits=$(grep -rIEn "$pat" "$d" 2>/dev/null | grep -v '\.map$' | grep -v 'node_modules' || true)
    if [ -n "$hits" ]; then
      echo "❌ Found commercial pattern: $pat"
      echo "$hits" | head -5
      echo
      found=1
    fi
  done
done

if [ $found -eq 1 ]; then
  echo
  echo "FAIL: commercial elements present in build output."
  echo "Remove these patterns from source before packaging."
  exit 1
fi

echo "✅ No commercial elements in $DIRS"
