#!/usr/bin/env python3

import argparse
import struct
import sys
import zipfile

ELF_MAGIC = b"\x7fELF"
ELFCLASS64 = 2
ELFDATA2LSB = 1
PT_LOAD = 1
MIN_PAGE_ALIGNMENT = 0x4000


def inspect_elf(name, data):
    if len(data) < 64 or data[:4] != ELF_MAGIC:
        raise ValueError("不是有效的 ELF 文件")
    if data[4] != ELFCLASS64 or data[5] != ELFDATA2LSB:
        raise ValueError("仅支持 64 位小端 ELF")

    program_header_offset = struct.unpack_from("<Q", data, 32)[0]
    program_header_size = struct.unpack_from("<H", data, 54)[0]
    program_header_count = struct.unpack_from("<H", data, 56)[0]
    if program_header_size < 56:
        raise ValueError("程序头大小无效")

    alignments = []
    for index in range(program_header_count):
        offset = program_header_offset + index * program_header_size
        if offset + 56 > len(data):
            raise ValueError("程序头超出文件范围")

        segment_type, _, file_offset, virtual_address, _, file_size, memory_size, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset
        )
        if segment_type != PT_LOAD:
            continue

        if file_size > memory_size:
            raise ValueError(f"PT_LOAD[{index}] 的文件大小大于内存大小")
        if file_offset > len(data) or file_size > len(data) - file_offset:
            raise ValueError(f"PT_LOAD[{index}] 的文件范围超出 ELF 大小")

        alignments.append(alignment)
        if alignment < MIN_PAGE_ALIGNMENT or alignment & (alignment - 1):
            raise ValueError(
                f"PT_LOAD[{index}] 对齐为 {alignment:#x}，要求至少 {MIN_PAGE_ALIGNMENT:#x}"
            )
        if file_offset % alignment != virtual_address % alignment:
            raise ValueError(
                f"PT_LOAD[{index}] 的文件偏移与虚拟地址不满足 {alignment:#x} 对齐"
            )

    if not alignments:
        raise ValueError("未找到 PT_LOAD 段")
    return min(alignments)


def verify_apk(apk_path):
    checked = 0
    failures = []
    with zipfile.ZipFile(apk_path) as apk:
        libraries = sorted(
            name for name in apk.namelist()
            if name.startswith("lib/arm64-v8a/") and name.endswith(".so")
        )
        if not libraries:
            raise ValueError("APK 中没有 arm64-v8a 原生库")

        for name in libraries:
            try:
                alignment = inspect_elf(name, apk.read(name))
                print(f"OK  {name}: 最小 PT_LOAD 对齐 {alignment:#x}")
                checked += 1
            except (KeyError, ValueError, struct.error) as error:
                failures.append(f"FAIL {name}: {error}")

    if failures:
        for failure in failures:
            print(failure, file=sys.stderr)
        return False

    print(f"已验证 {checked} 个 arm64-v8a 原生库支持 16 KB 页面。")
    return True


def main():
    parser = argparse.ArgumentParser(description="验证 APK 中 arm64 ELF 的 16 KB 页面兼容性")
    parser.add_argument("apk", help="待检查的 APK 路径")
    args = parser.parse_args()

    try:
        success = verify_apk(args.apk)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"验证失败: {error}", file=sys.stderr)
        return 1
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
