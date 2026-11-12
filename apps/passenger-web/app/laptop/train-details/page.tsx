import { ReferenceHeader } from "../../../components/reference-header";
import { TrainDetailsExperience } from "../../../components/train-details-experience";

export const metadata = {
  title: "Train route · Laptop",
};

export default function LaptopTrainDetailsPage() {
  return (
    <div className="page-shell bg-[#040914] text-[#eaf3ff]">
      <ReferenceHeader experience="laptop" activeTab="train-info" />
      <TrainDetailsExperience device="laptop" />
      <footer className="site-footer laptop-footer">
        <span>RailConnect — independent passenger timetable search</span>
      </footer>
    </div>
  );
}
