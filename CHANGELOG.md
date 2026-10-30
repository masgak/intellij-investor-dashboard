# Changelog

## 1.27.3

### 🐛 Bug Fixes / 错误修复

- Remove the automatic value-screen popup after scheduled screening; results still update in the Screen tab / 移除定时选股完成后的弹窗，筛选结果仍会更新到「选股」标签

## 1.27.2

### 🐛 Bug Fixes / 错误修复

- Hide business sector and listing board columns on Hong Kong, US, and Crypto tabs instead of showing empty placeholders / 港股、美股、加密货币 Tab 不再显示业务板块与上市板块列

## 1.27.1

### ✨ New Features / 新功能

- Paper trading shows cumulative P&L with percentage, applies A-share T+1 on sells, and adds a one-click paper buy action on the screen tab / 模拟交易累计盈亏显示百分比，A 股卖出遵循 T+1，选股页增加一键模拟购入

### 🐛 Bug Fixes / 错误修复

- Business sector and listing board columns show only for A-shares; Hong Kong and US rows display "-" / 业务板块与上市板块仅 A 股显示，港股和美股显示为「-」

## 1.27.0

### ✨ New Features / 新功能

- Quote tables show each company's business sector, such as semiconductors or consumer electronics. A-shares also show the listing board: main board, ChiNext, STAR Market, or Beijing Stock Exchange / 行情表显示公司业务板块，例如半导体、消费电子。A 股同时显示上市板块：主板、创业板、科创板或北交所

## 1.26.3

### ✨ New Features / 新功能

- Favorite tabs have a filter box that matches code, name, or pinyin / 自选页工具栏增加筛选框，可按代码、名称或拼音过滤

## 1.26.2

### 🐛 Bug Fixes / 错误修复

- Quote tables open in watchlist order instead of sorting by change percent / 打开行情列表时按自选顺序显示，不再默认按涨幅排序

## 1.26.1

### 🐛 Bug Fixes / 错误修复

- The paper-trade buy list shows company names / 模拟买入的下拉列表改为显示公司名

## 1.26.0

### ✨ New Features / 新功能

- Added a paper-trading tab. Buys and sells use the latest quote, and each refresh shows today's change amount and percent against the simulated capital / 新增模拟买卖标签。按最新价买卖，行情刷新时根据模拟金额显示今日涨跌额和涨跌幅

## 1.25.1

### 🐛 Bug Fixes / 错误修复

- Plugin HTTP calls connect directly and no longer follow the IDE system proxy / 插件的网络请求改为直连，不再走 IDE 的系统代理

## 1.25.0

### ✨ New Features / 新功能

- The screened stock case is written by a configured OpenAI-compatible model. Base URL, API key, model list, and thinking level can be set from the screen toolbar or Stocker settings / 选股详情里的买卖理由改为由已配置的模型撰写。可在选股工具栏或 Stocker 设置中填写接口地址、API Key、模型列表和思考强度

## 1.24.0

### ✨ New Features / 新功能

- Clicking a screened stock opens the buy or sell case, plus the company profile and the management team / 在选股结果里点击股票，可查看买入或卖出理由、公司介绍和高管团队

## 1.23.1

### 🐛 Bug Fixes / 错误修复

- Daily backtests now load forward-adjusted bars from Tencent. One symbol that fails to download no longer cancels the rest of the favorites run / 日线回测改从腾讯拉前复权 K 线。单只下载失败不再中断其余自选的回测

## 1.23.0

### ✨ New Features / 新功能

- Added a daily-bar backtest tab. Strategies run one bar at a time through an A-share broker, with buy-and-hold, moving-average cross, and turtle breakout / 新增日线回测标签。策略按 vn.py / backtrader 的方式逐根 K 线推进，内置买入持有、均线交叉和海龟突破，可回测单只股票或全部 A 股自选

## 1.22.0

### ✨ New Features / 新功能

- The pre-open screen can switch among four published fundamental strategies: Tang Chao, Graham PE×PB, Greenblatt magic formula, and the investool quality filter / 开盘前选股可在四套公开策略间切换：老唐、格雷厄姆 PE×PB、格林布拉特神奇公式、investool 质量过滤

## 1.21.1

### 🐛 Bug Fixes / 错误修复

- The value screen keeps the stock table as the main area. The screening rules open from the help button, and the daily switch sits on the status line / 选股页以表格为主。筛选规则改到帮助按钮里，每天自动筛选的开关放到状态栏

## 1.21.0

### ✨ New Features / 新功能

- Added a pre-open A-share screen based on Tang Chao's Value Investing Handbook. It runs daily at 08:50 Beijing time and lists names that pass the quality filter, marked as buy, hold, or sell / 新增开盘前 A 股选股。按《价值投资实战手册》每天 8:50（北京时间）筛选，并标出理想买点、持有和卖出

## 1.20.2

### ✨ New Features / 新功能

- Added custom stock groups. Each group is its own tool window tab and can be created, renamed, and edited from Manage Groups / 新增自定义股票分组。每个分组会作为独立的工具窗口标签页，可在“管理分组”中创建、重命名和编辑
- Added a default sort column and direction in Settings, used when the table has no manual sort / 在设置中新增默认排序列和排序方向，表格未手动排序时自动应用

### 🐛 Bug Fixes / 错误修复

- Fixed Sina ETF and LOF suggestions so on-exchange funds use the numeric code and duplicate rows are dropped / 修复新浪 ETF 与 LOF 搜索建议：场内基金使用数字代码，并去掉重复结果

## 1.20.1

### 🐛 Bug Fixes / 错误修复

- Fixed the Windows right-click delete race in the table popup menu so stock removal works reliably under the new UI / 修复 Windows 下表格右键删除菜单的竞态问题，确保在新版 UI 中稳定删除股票

## 1.20.0

### ✨ New Features / 新功能

- Added a Net Profit column computed from current price, cost price, and holdings, with sorting and color coding support / 新增净收益额列，基于现价、成本价和持仓自动计算，并支持排序与颜色编码

### 🔧 Maintenance / 维护

- Upgraded the IntelliJ Platform Gradle plugin to 2.12.0 / 升级 IntelliJ Platform Gradle 插件到 2.12.0

## 1.19.0

### ✨ Improvements / 改进

- Restored the toolbar "Stop Refresh" action so scheduled quote updates can be paused without clearing the current table data / 恢复工具栏“停止刷新”操作，可在不清空当前表格数据的情况下暂停定时行情更新

## 1.18.2

### 🐛 Bug Fixes / 错误修复

- Fixed right-click delete sometimes failing because the popup action could lose the selected row before deletion / 修复右键删除偶发失效的问题：弹出菜单执行前表格选中行可能已丢失
- Removed duplicate table row deletion notifications during stock removal to avoid inconsistent refresh behavior / 移除删除股票时重复触发表格行删除通知的问题，避免刷新行为不一致

## 1.18.1

### 🌐 i18n / 国际化

- Fixed action text/description localization in Tools menu and tool window actions; labels now follow the selected plugin language immediately / 修复工具菜单与工具窗口操作项的文本和描述本地化问题；标签现在会立即跟随插件语言设置
- Aligned action naming style for better consistency across English and Chinese labels / 统一中英文操作项命名风格，提升一致性

### 🐛 Bug Fixes / 错误修复

- Fixed "Clear Favorites" to clear all markets including Crypto and trigger proper view refresh / 修复“清空自选”未覆盖加密货币的问题，并确保触发正确的视图刷新
- Fixed crypto symbol validation to use crypto quote provider instead of stock quote provider / 修复加密货币代码校验使用错误数据源的问题（改为使用加密行情源）

## 1.18.0

### 🌐 i18n / 国际化

- Fixed language switching: the plugin language setting now works correctly and applies immediately to the table view / 修复语言切换：插件语言设置现已正常工作，更改后立即应用到表格视图
- Notifications now follow the plugin language setting instead of showing dual-language text / 通知消息现在遵循插件语言设置，不再同时显示中英文

### 🐛 Bug Fixes / 错误修复

- Fixed settings reverting when clicking Apply then OK in the settings dialog / 修复在设置中先点击应用再点击确定时设置被还原的问题
- Fixed table column visibility breaking after language switch (now stored as locale-independent identifiers with automatic migration) / 修复语言切换后表格列可见性失效的问题（现以语言无关的标识符存储，并自动迁移旧配置）

### 🎨 UI Improvements / 界面改进

- Reorganized settings layout into three focused groups: General, Data Provider, and Table Display / 重新整理设置页面为三个分组：通用、数据提供商和表格显示

## 1.17.0

### ✨ New Features / 新功能

- Added settings button to tool window action bar (right-aligned) for quick access to Stocker settings / 在工具窗口操作栏添加设置按钮（右对齐），快速访问 Stocker 设置

## 1.16.2

### ✨ Improvements / 改进

- Enhanced Cost column color coding: displays up color when cost is below current price (profit) and down color when cost is above current price (loss) / 增强成本列颜色编码：成本低于当前价格时显示上涨颜色（盈利），成本高于当前价格时显示下跌颜色（亏损）

## 1.16.1

### ✨ Improvements / 改进

- Added right-click row popup menu in table view with one-click stock deletion / 在表格视图中添加右键行弹出菜单，支持一键删除股票
- Improved popup delete menu hover styling for better theme consistency and visibility / 改进删除菜单悬浮样式，提升主题一致性与可见性

## 1.16.0

### ✨ New Features / 新功能

- Added cost price and holdings columns with visibility toggling for enhanced portfolio tracking / 添加成本价和持仓列，支持显示切换，增强投资组合跟踪

### 🎨 UI Improvements / 界面改进

- Refined table rendering with improved padding and border styling / 优化表格渲染，改进内边距和边框样式
- Adopted IDE theme colors for table selection to ensure better visual consistency / 采用 IDE 主题颜色用于表格选中状态，确保更好的视觉一致性

## 1.15.0

### ✨ New Features / 新功能

- Added cryptocurrency support (crypt support) / 添加加密货币支持
- Added more table columns for enhanced data display / 添加更多表格列以增强数据显示

## 1.14.1

### 🐛 Bug Fixes / 错误修复

- Fixed table sorting not restoring original order when switching back to unsorted state / 修复表格排序在切换回未排序状态时无法恢复原始顺序的问题
- Fixed color pattern not immediately reflecting in tables when clicking Apply in settings (now updates instantly without data refetch) / 修复在设置中点击应用时颜色模式未立即在表格中反映的问题（现在无需重新获取数据即可立即更新）
- Improved settings granularity: color pattern changes no longer trigger unnecessary data refetching / 改进设置粒度：颜色模式更改不再触发不必要的数据重新获取

## 1.14.0

### 🚀 Performance & Memory Optimizations / 性能和内存优化

- **Critical Memory Leak Fixes / 关键内存泄漏修复:**
  - Fixed message bus connection leaks in tool window (15+ connections per window now properly disposed) / 修复工具窗口消息总线连接泄漏（每个窗口15+连接现已正确释放）
  - Fixed project map memory leak (StockerApp instances now cleaned up on project close) / 修复项目映射内存泄漏（项目关闭时清理StockerApp实例）
  - Fixed HTTP response leaks (all responses now properly closed with automatic resource management) / 修复HTTP响应泄漏（所有响应现通过自动资源管理正确关闭）
  - Fixed table view disposal leak (static registry now properly cleaned up) / 修复表格视图释放泄漏（静态注册表现已正确清理）

- **HTTP & Network Improvements / HTTP和网络改进:**
  - Added connection timeouts (10s connect, 15s socket, 5s pool request) to prevent hanging threads / 添加连接超时（10秒连接，15秒套接字，5秒池请求）防止线程挂起
  - Properly close all HTTP connections with `.use{}` pattern / 使用`.use{}`模式正确关闭所有HTTP连接
  - Enhanced connection pool configuration / 增强连接池配置

- **Performance Optimizations / 性能优化:**
  - Consolidated scheduled tasks: reduced from 4 to 1 task (50% reduction in HTTP requests) / 合并计划任务：从4个减少到1个（HTTP请求减少50%）
  - Optimized table sorting: removed data duplication (50% memory reduction during sorting) / 优化表格排序：移除数据复制（排序时内存减少50%）
  - Reduced thread pool size from 4 to 1 threads (75% reduction) / 线程池大小从4减少到1（减少75%）
  - Implemented proper Disposable pattern for resource cleanup / 实现适当的Disposable模式进行资源清理

- **Architectural Improvements / 架构改进:**
  - Added ProjectManagerListener for automatic cleanup on project close / 添加ProjectManagerListener在项目关闭时自动清理
  - Improved encapsulation in StockerAppManager with proper public API / 改进StockerAppManager的封装与适当的公共API
  - Enhanced tool window lifecycle management / 增强工具窗口生命周期管理

## 1.13.1

- Add sortable table columns with three-state sorting (ascending, descending, unsorted) / 添加可排序的表格列，支持三态排序（升序、降序、不排序）

## 1.13.0

- Add customizable table column display settings / 添加可自定义的表格列显示设置

## 1.12.3

- Improve table selection clearing behavior

## 1.12.2

- Fix index names not obeying Pinyin display mode
- Add Hang Seng Tech Index (恒生科技指数, HSTECH)

## 1.12.1

- Add custom stock name feature with edit functionality in management dialog (custom names take highest priority)
- Enhanced management dialog UI with three-column layout (Code, Original Name, Custom Name)
- Enhanced suggestion dialog UI with improved search results layout

## 1.12.0

- Add Pinyin support for stock names with display settings
- Enhanced welcome and release note notifications
- Various technical improvements and dependency updates

## 1.11.1

- Fix IntelliJ 2024.2 series compatibility issues

## 1.11.0

- Fix IntelliJ 2023.3 series compatibility issues

## 1.10.2

- Fix compiler warnings

## 1.10.1

- Add A-Share Convertible Bond support

## 1.10.0

- Bring back SINA provider support

## 1.9.1

- Fix three digits price accuracy issue

## 1.9.0

- New management dialog: batch delete & reorder symbols

## 1.8.1

- Fix compatibility issue

## 1.8.0

- Support JetBrains 2022 EAP

## 1.7.0

- Replace Sina API with Tencent API due to Sina API is closed
- Crypto support is temporary removed since Sina API is no longer available

## 1.6.1

- Support JetBrains 2021.3 series

## 1.6.0

- Enhanced setting window UI
- Enhanced search dialog UI
- Enhanced management dialog UI

## 1.5.3

- Fixed multiple projects compatibility [#12](https://github.com/WhiteVermouth/intellij-investor-dashboard/issues/12)
- Fixed API compatibility

## 1.5.2

- Support IntelliJ 2021.2 EAP

## 1.5.1

- Fixed price accuracy [#11](https://github.com/WhiteVermouth/intellij-investor-dashboard/issues/11)

## 1.5.0

- New action: Stop refresh
- New pane: Crypto
- Deprecated: Tencent API

## 1.4.4

- Fix Long stock name wrapping
- Fix search bar text change event

## 1.4.3

- Fixed Android Studio compatibility
- Fixed missed ETF in search results

## 1.4.2

- Fix compatibility issue

## 1.4.1

- Enhanced stock management dialogs

## 1.4.0

- New Stock Add Dialog
- New Stock Delete Dialog
- Some enhancement and bug fix

## 1.3.7

- Support JetBrains 2019 series

## 1.3.6

- Add backward compatibility until 2020.1

## 1.3.5

- Fixed compatibility issue

## 1.3.4

- Support disable Red/Green color pattern

## 1.3.3

- Bug fix

## 1.3.2

- Bug fix

## 1.3.1

- Add right-click popup menu to delete code(s)

## 1.3.0

- Add index view

## 1.2.1

- Enhanced UI
- Bug fix

## 1.2.0

- Add a tab: ALL
- Enhanced UI

## 1.1.0

- Adopt more distinct colors
- Improve Last Update At datetime
- Add a new quote provider: Tencent

## 1.0.0

- Stocker: a stock quote dashboard
