# Turns a binary file into a C++ source with a byte array, so the default network is built into
# engine_v3.exe (like v2 bundles best.nnue in its jar). Called by CMakeLists.txt as:
#   cmake -DINPUT=<file.nnue> -DOUTPUT=<embedded_net.cpp> -P embed.cmake
if(NOT EXISTS "${INPUT}")
  file(WRITE "${OUTPUT}"
    "// No network file found at build time: the engine needs EvalFile.\n"
    "#include <cstddef>\nnamespace v3::nnue { extern const unsigned char EMBEDDED_NET[] = {0};\n"
    "extern const std::size_t EMBEDDED_NET_SIZE = 0; }\n")
  return()
endif()
file(READ "${INPUT}" hex HEX)
string(LENGTH "${hex}" hex_len)
math(EXPR size "${hex_len} / 2")
string(REGEX REPLACE "([0-9a-f][0-9a-f])" "0x\\1," bytes "${hex}")
# Line breaks every 32 bytes keep the generated file readable for compilers and editors.
string(REGEX REPLACE "((0x..,){32})" "\\1\n" bytes "${bytes}")
file(WRITE "${OUTPUT}"
  "// Generated from ${INPUT} by cmake/embed.cmake - do not edit.\n"
  "#include <cstddef>\nnamespace v3::nnue {\n"
  "extern const unsigned char EMBEDDED_NET[] = {\n${bytes}\n};\n"
  "extern const std::size_t EMBEDDED_NET_SIZE = ${size};\n}\n")
